package com.alexlee.springmvc.test;

import com.alexlee.springmvc.annotation.LeeAutowired;
import com.alexlee.springmvc.annotation.LeeController;
import com.alexlee.springmvc.annotation.LeeRequestMapping;
import com.alexlee.springmvc.annotation.LeeRequestParam;

import javax.servlet.http.HttpServletResponse;

/**
 * @author alexlee
 */
@LeeController
@LeeRequestMapping("/alexlee")
public class MathController {

    @LeeAutowired
    private MathService mathService;

    @LeeRequestMapping("/add")
    public void add(@LeeRequestParam("a") String a, @LeeRequestParam("b") String b, HttpServletResponse response) {
        try {
            int result = mathService.add(Integer.parseInt(a), Integer.parseInt(b));
            response.getWriter().write(a + "+" + b + "=" + result);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


}
