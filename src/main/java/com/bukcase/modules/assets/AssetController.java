package com.bukcase.modules.assets;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/assets")
class AssetController {

    private final AssetService service;

    AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    List<AssetView> list() {
        return service.list().stream().map(AssetView::of).toList();
    }

    @GetMapping("/{id}")
    AssetView get(@PathVariable long id) {
        return AssetView.of(service.get(id));
    }

    @PatchMapping("/{id}")
    AssetView rename(@PathVariable long id, @RequestBody RenameRequest request) {
        return AssetView.of(service.rename(id, request.name()));
    }

    @GetMapping("/categories")
    List<String> categories() {
        return service.categories().stream().map(AssetCategory::getName).toList();
    }

    record RenameRequest(String name) {
    }

    record AssetView(long id, String name, String category, String assignedTo) {
        static AssetView of(Asset asset) {
            return new AssetView(asset.getId(), asset.getName(), asset.getCategory().getName(),
                    asset.getEmployee() == null ? null : asset.getEmployee().getFullName());
        }
    }
}
