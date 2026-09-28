package com.bukcase.modules.assets;

import com.bukcase.authz.api.AccessLevel;
import com.bukcase.authz.api.Authorizer;
import com.bukcase.authz.api.RequiresAccess;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService {

    private final AssetRepository assets;
    private final AssetCategoryRepository categories;
    private final Authorizer authorizer;

    public AssetService(AssetRepository assets, AssetCategoryRepository categories, Authorizer authorizer) {
        this.assets = assets;
        this.categories = categories;
        this.authorizer = authorizer;
    }

    @Transactional(readOnly = true)
    public List<Asset> list() {
        return assets.findAll(authorizer.readable(Asset.class));
    }

    @Transactional(readOnly = true)
    public Asset get(long id) {
        Asset asset = find(id);
        authorizer.check(AccessLevel.READ, asset);
        return asset;
    }

    @Transactional
    public Asset rename(long id, String newName) {
        Asset asset = find(id);
        authorizer.check(AccessLevel.WRITE, asset);
        asset.rename(newName);
        return asset;
    }

    @RequiresAccess(resource = "ASSETS")
    @Transactional(readOnly = true)
    public List<AssetCategory> categories() {
        return categories.findAll();
    }

    private Asset find(long id) {
        return assets.findById(id).orElseThrow(() -> new NoSuchElementException("Asset " + id + " not found"));
    }
}
