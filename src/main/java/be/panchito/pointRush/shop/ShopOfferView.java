package be.panchito.pointRush.shop;

import org.bukkit.Material;

import java.util.List;

/** Vaste perks (prijs in Rush-tegoed; Rush-munten worden automatisch verrekend). */
public record ShopOfferView(
        String perkId,
        ShopCategory category,
        String displayName,
        List<String> descriptionLines,
        Material icon,
        int priceCredits
) {
}
