package com.example.demo.skill;

import java.util.List;

public interface BotSkill {
    String name();

    String description();

    List<String> keywords();

    String execute(String userMessage);
}
