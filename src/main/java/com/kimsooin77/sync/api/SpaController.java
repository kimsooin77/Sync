package com.kimsooin77.sync.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class SpaController {

    @RequestMapping(
            value = {"/", "/login", "/employees", "/integrations"},
            method = {RequestMethod.GET, RequestMethod.HEAD})
    public String index() {
        return "forward:/index.html";
    }
}
