package io.raindrops.storage.controller;

import io.raindrops.storage.config.PeerConfig;
import io.raindrops.storage.repository.DropRepository;
import io.raindrops.storage.repository.RainMapRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final DropRepository dropRepository;
    private final RainMapRepository rainMapRepository;
    private final PeerConfig peerConfig;

    public DashboardController(DropRepository dropRepository,
                               RainMapRepository rainMapRepository,
                               PeerConfig peerConfig) {
        this.dropRepository = dropRepository;
        this.rainMapRepository = rainMapRepository;
        this.peerConfig = peerConfig;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("nodeId", peerConfig.getNodeId());
        model.addAttribute("dropCount", dropRepository.count());
        model.addAttribute("rainMapCount", rainMapRepository.count());
        model.addAttribute("peers", peerConfig.getPeerUrls());
        return "dashboard";
    }
}