package com.example.payment.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Forwards client-side application routes to the React entry point. */
@Controller
public class FrontendController {

    @GetMapping({"/", "/payments", "/users", "/users/{id}"})
    public String forwardToFrontend() {
        return "forward:/index.html";
    }
}
