package com.example.demo.agent.planner;

import com.example.demo.agent.model.AgentCapability;
import com.example.demo.agent.model.AgentTaskDefinition;
import com.example.demo.agent.model.AgentPlan;
import com.example.demo.config.AgentConfig;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AgentPlanValidator {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z][a-z0-9_-]{0,63}$");
    private final AgentConfig config;

    public AgentPlanValidator(AgentConfig config) {
        this.config = config;
    }

    public void validate(AgentPlan plan, List<AgentCapability> capabilities) {
        if (plan == null || !StringUtils.hasText(plan.goal())) {
            throw invalid("Agent计划缺少最终目标");
        }
        if (!StringUtils.hasText(plan.summary())) {
            throw invalid("Agent计划缺少整体说明");
        }
        if (plan.tasks().size() < config.getMinTasks()
                || plan.tasks().size() > config.getMaxTasks()) {
            throw invalid("Agent任务数量必须在%d到%d之间"
                    .formatted(config.getMinTasks(), config.getMaxTasks()));
        }
        if (plan.completionCriteria().isEmpty()
                || plan.completionCriteria().stream().anyMatch(value -> !StringUtils.hasText(value))) {
            throw invalid("Agent计划至少需要一条有效的完成标准");
        }

        Set<String> capabilityNames = validateCapabilities(capabilities);
        Map<String, AgentTaskDefinition> tasksById = new HashMap<>();
        Set<String> usedCapabilities = new HashSet<>();
        for (AgentTaskDefinition task : plan.tasks()) {
            validateTask(task, capabilityNames);
            if (tasksById.putIfAbsent(task.id(), task) != null) {
                throw invalid("Agent任务ID重复：" + task.id());
            }
            usedCapabilities.add(task.capability());
        }
        if (usedCapabilities.size() < 2) {
            throw invalid("长任务计划至少需要使用两种不同能力");
        }

        for (AgentTaskDefinition task : plan.tasks()) {
            for (String dependency : task.dependsOn()) {
                if (!tasksById.containsKey(dependency)) {
                    throw invalid("任务%s依赖不存在的任务：%s".formatted(task.id(), dependency));
                }
                if (task.id().equals(dependency)) {
                    throw invalid("任务不能依赖自身：" + task.id());
                }
            }
        }

        validateAcyclic(tasksById);
        validateClosedLoop(plan.tasks(), tasksById);
    }

    private Set<String> validateCapabilities(List<AgentCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            throw invalid("Agent没有可用能力，无法制定计划");
        }
        Set<String> names = new HashSet<>();
        for (AgentCapability capability : capabilities) {
            if (capability == null || !StringUtils.hasText(capability.name())
                    || capability.type() == null
                    || !StringUtils.hasText(capability.description())) {
                throw invalid("Agent能力定义不完整");
            }
            if (!names.add(capability.name())) {
                throw invalid("Agent能力名称重复：" + capability.name());
            }
        }
        return names;
    }

    private void validateTask(AgentTaskDefinition task, Set<String> capabilityNames) {
        if (task == null || !StringUtils.hasText(task.id())
                || !VALID_ID.matcher(task.id()).matches()) {
            throw invalid("任务ID必须以小写字母开头，只能包含小写字母、数字、下划线或短横线");
        }
        if (!StringUtils.hasText(task.title()) || !StringUtils.hasText(task.description())
                || !StringUtils.hasText(task.expectedOutput())) {
            throw invalid("任务%s的标题、说明或预期输出不完整".formatted(task.id()));
        }
        if (!capabilityNames.contains(task.capability())) {
            throw invalid("任务%s使用了未注册能力：%s".formatted(task.id(), task.capability()));
        }
        if (task.maxAttempts() < 1 || task.maxAttempts() > 3) {
            throw invalid("任务%s的maxAttempts必须在1到3之间".formatted(task.id()));
        }
        if (new HashSet<>(task.dependsOn()).size() != task.dependsOn().size()) {
            throw invalid("任务%s包含重复依赖".formatted(task.id()));
        }
    }

    private void validateAcyclic(Map<String, AgentTaskDefinition> tasksById) {
        Map<String, VisitState> states = new HashMap<>();
        for (String taskId : tasksById.keySet()) {
            visit(taskId, tasksById, states);
        }
    }

    private void visit(
            String taskId,
            Map<String, AgentTaskDefinition> tasksById,
            Map<String, VisitState> states) {
        VisitState current = states.get(taskId);
        if (current == VisitState.VISITING) {
            throw invalid("Agent任务依赖存在循环，涉及任务：" + taskId);
        }
        if (current == VisitState.VISITED) {
            return;
        }
        states.put(taskId, VisitState.VISITING);
        for (String dependency : tasksById.get(taskId).dependsOn()) {
            visit(dependency, tasksById, states);
        }
        states.put(taskId, VisitState.VISITED);
    }

    private void validateClosedLoop(
            List<AgentTaskDefinition> tasks,
            Map<String, AgentTaskDefinition> tasksById) {
        Set<String> dependedOn = new HashSet<>();
        tasks.forEach(task -> dependedOn.addAll(task.dependsOn()));
        boolean hasFinalSynthesis = tasks.stream()
                .filter(task -> !dependedOn.contains(task.id()))
                .anyMatch(task -> ancestors(task.id(), tasksById, new HashSet<>()).size()
                        == tasks.size() - 1);
        if (!hasFinalSynthesis) {
            throw invalid("计划必须包含一个汇总全部前置结果的最终任务，形成完整闭环");
        }
    }

    private Set<String> ancestors(
            String taskId,
            Map<String, AgentTaskDefinition> tasksById,
            Set<String> collected) {
        for (String dependency : tasksById.get(taskId).dependsOn()) {
            if (collected.add(dependency)) {
                ancestors(dependency, tasksById, collected);
            }
        }
        return collected;
    }

    private AgentPlanValidationException invalid(String message) {
        return new AgentPlanValidationException(message);
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
