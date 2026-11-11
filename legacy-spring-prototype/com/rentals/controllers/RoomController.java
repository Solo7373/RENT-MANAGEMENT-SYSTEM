package com.rentals.controllers;

import com.rentals.rooms.Room;
import com.rentals.rooms.RoomRepository;
import com.rentals.properties.PropertyRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/rooms")
public class RoomController {

    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;

    public RoomController(RoomRepository roomRepository, PropertyRepository propertyRepository) {
        this.roomRepository = roomRepository;
        this.propertyRepository = propertyRepository;
    }

    @GetMapping
    public String listRooms(Model model) {
        model.addAttribute("rooms", roomRepository.findAll());
        return "rooms/list";
    }

    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("room", new Room());
        model.addAttribute("properties", propertyRepository.findAll());
        return "rooms/add";
    }

    @PostMapping("/add")
    public String addRoom(@ModelAttribute Room room) {
        roomRepository.save(room);
        return "redirect:/rooms";
    }
}
