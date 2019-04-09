package com.alexlee.springmvc;

import com.alexlee.springmvc.LeeDispatcherServlet;

public class SimpleTest {
    public static void main(String[] args) {
        System.out.println(LeeDispatcherServlet.class.getResource("").getPath());
        System.out.println(LeeDispatcherServlet.class.getClassLoader().getResource("application.properties"));

    }
}
