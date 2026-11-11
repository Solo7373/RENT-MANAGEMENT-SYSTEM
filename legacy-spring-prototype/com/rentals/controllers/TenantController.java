package com.rentals.controllers;

import com.rentals.tenants.Tenant;
import com.rentals.tenants.TenantRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/tenants")
public class TenantController {

    private final TenantRepository tenantRepository;

    public TenantController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @GetMapping
    public String listTenants(Model model) {
        model.addAttribute("tenants", tenantRepository.findAll());
        return "tenants/list";
    }

    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("tenant", new Tenant());
        return "tenants/add";
    }

    @PostMapping("/add")
    public String addTenant(@ModelAttribute Tenant tenant) {
        tenantRepository.save(tenant);
        return "redirect:/tenants";
    }
}
