package com.alexlee.springmvc.test;

import com.alexlee.springmvc.annotation.LeeComponent;

@LeeComponent
public class MathService {
    public int add(int a, int b) {
        return a + b;
    }
}
