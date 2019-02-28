package com.yongge.demo.mvc.action;

import com.service.IDemoService;
import com.yongge.mvcframework.annotation.Autowired;
import com.yongge.mvcframework.annotation.Controller;
import com.yongge.mvcframework.annotation.RequestMapping;
import com.yongge.mvcframework.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping("/demo")
public class DemoAction {
    @Autowired private IDemoService demoService;
    @RequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp,@RequestParam("name") String name){
        String result = demoService.get(name);
        try{
            resp.getWriter().write(result);
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}
