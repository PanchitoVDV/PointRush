package be.panchito.pointRush.shop;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShopCatalog {

    private static final List<ShopOfferView> OFFERS = build();

    private ShopCatalog() {
    }

    public static List<ShopOfferView> allOffers() {
        return OFFERS;
    }

    public static List<ShopOfferView> forCategory(ShopCategory cat) {
        List<ShopOfferView> out = new ArrayList<>();
        for (ShopOfferView o : OFFERS) {
            if (o.category() == cat) {
                out.add(o);
            }
        }
        return out;
    }

    private static List<ShopOfferView> build() {
        List<ShopOfferView> list = new ArrayList<>();
        Collections.addAll(list,
                new ShopOfferView(
                        ShopPerks.PARKOUR_TAILWIND,
                        ShopCategory.PARKOUR,
                        "Snelle sjaal",
                        List.of(
                                "Speed I voor de hele run.",
                                "Lekker voor lange sprongen."
                        ),
                        Material.PHANTOM_MEMBRANE,
                        80
                ),
                new ShopOfferView(
                        ShopPerks.PARKOUR_SPRINGHEEL,
                        ShopCategory.PARKOUR,
                        "Springende schoenen",
                        List.of(
                                "Jump Boost I vanaf het startschot.",
                                "Extra hoogte zonder rare shortcuts."
                        ),
                        Material.RABBIT_FOOT,
                        96
                ),
                new ShopOfferView(
                        ShopPerks.PARKOUR_CLOUDSTEP,
                        ShopCategory.PARKOUR,
                        "Valdemper",
                        List.of(
                                "Slow Falling een paar seconden bij GO.",
                                "Redt een mis-sprong."
                        ),
                        Material.WHITE_CARPET,
                        112
                ),
                new ShopOfferView(
                        ShopPerks.TAG_GHOST_PASS,
                        ShopCategory.TNT_TAG,
                        "Spookmodus",
                        List.of(
                                "De eerste tag tegen jou gaat mis.",
                                "Daarna ben je weer gewoon kwetsbaar."
                        ),
                        Material.IRON_INGOT,
                        104
                ),
                new ShopOfferView(
                        ShopPerks.TAG_IRON_RUSH,
                        ShopCategory.TNT_TAG,
                        "IJzeren pantser",
                        List.of(
                                "Resistance I aan het begin van elke ronde.",
                                "Minder pijn van panische passes."
                        ),
                        Material.SHIELD,
                        72
                ),
                new ShopOfferView(
                        ShopPerks.TAG_SECOND_WIND,
                        ShopCategory.TNT_TAG,
                        "Hersteldrankje",
                        List.of(
                                "Regeneration I aan het begin van elke ronde.",
                                "Even bijtanken tussen rondes."
                        ),
                        Material.GOLDEN_APPLE,
                        88
                ),
                new ShopOfferView(
                        ShopPerks.RUN_GRIP_SOLES,
                        ShopCategory.TNT_RUN,
                        "Plakzolen",
                        List.of(
                                "De blokken onder je verdwijnen iets later.",
                                "Meer tijd om te rennen."
                        ),
                        Material.LEATHER_BOOTS,
                        96
                ),
                new ShopOfferView(
                        ShopPerks.RUN_FEATHER_STEP,
                        ShopCategory.TNT_RUN,
                        "Lichte sprong",
                        List.of(
                                "Jump Boost I tijdens de hele run.",
                                "Handig voor kleine hoogtes."
                        ),
                        Material.FEATHER,
                        72
                ),
                new ShopOfferView(
                        ShopPerks.RUN_STEADY_CORE,
                        ShopCategory.TNT_RUN,
                        "Stevig pantser",
                        List.of(
                                "Resistance I tijdens de hele run.",
                                "Iets rustiger als de vloer wegzakt."
                        ),
                        Material.NETHERITE_SCRAP,
                        88
                ),
                new ShopOfferView(
                        ShopPerks.RACE_NITRO_GLUG,
                        ShopCategory.RACE,
                        "Boost-shot",
                        List.of(
                                "Speed II een paar seconden bij GO.",
                                "Gas erop in het begin."
                        ),
                        Material.HONEY_BOTTLE,
                        120
                ),
                new ShopOfferView(
                        ShopPerks.RACE_ROLL_CAGE,
                        ShopCategory.RACE,
                        "Botskap",
                        List.of(
                                "Resistance II een paar seconden bij GO.",
                                "Minder last van bumps."
                        ),
                        Material.IRON_DOOR,
                        96
                ),
                new ShopOfferView(
                        ShopPerks.RACE_PIT_CREW,
                        ShopCategory.RACE,
                        "Herstelslurpie",
                        List.of(
                                "Regeneration I een paar seconden bij GO.",
                                "Kleine heal voor de start."
                        ),
                        Material.COCOA_BEANS,
                        72
                )
        );
        return Collections.unmodifiableList(list);
    }
}
