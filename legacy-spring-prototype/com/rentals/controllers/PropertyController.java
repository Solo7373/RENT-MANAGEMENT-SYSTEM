package com.rentals.controllers;

import com.rentals.properties.Property;
import com.rentals.properties.PropertyRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/properties")
public class PropertyController {

    private final PropertyRepository propertyRepository;

    public PropertyController(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @GetMapping
    public String listProperties(Model model) {
        model.addAttribute("properties", propertyRepository.findAll());
        return "properties/list";
    }

    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("property", new Property());
        return "properties/add";
    }

    @PostMapping("/add")
    public String addProperty(@ModelAttribute Property property) {
        propertyRepository.save(property);
        return "redirect:/properties";
    }
}
