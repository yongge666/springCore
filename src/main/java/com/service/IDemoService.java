package com.service;

import com.yongge.mvcframework.annotation.Service;

@Service
public class IDemoService {
    public String get(String name){
        return name;
    }
}
