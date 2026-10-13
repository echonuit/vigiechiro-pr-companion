package fr.univ_amu.iut.perf.outils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/// Les jalons d'un import, et les écarts de la sonde situés parmi eux (#6175).
///
/// Un écart de plus de 200 ms dit qu'une image a tardé, pas ce qui tenait le fil JavaFX. Le banc marque
/// donc les étapes que l'écran traverse, chacune à son premier instant, et rapporte chaque écart au
/// jalon qui le précède, à ceux qu'il contient et à celui qui le suit. Un jalon tombé dans un écart
/// désigne le travail qui a retardé l'image.
///
/// Les instants sont ceux de `System.nanoTime()`, l'horloge que la sonde reçoit à chaque image. Ils
/// s'écrivent depuis le premier jalon marqué, le clic sur « Importer ».
final class DatationDesEcarts {

    /// Un écart entre deux images, avec les instants de l'une et de l'autre.
    record EcartDate(long debutNs, long finNs) {
        double ms() {
            return (finNs - debutNs) / 1e6;
        }
    }

    private record Jalon(String nom, long ns) {
        String entreGuillemets() {
            return "« " + nom + " »";
        }
    }

    private final List<Jalon> jalons = new ArrayList<>();

    /// Marque `nom` à l'instant `ns`. Un jalon déjà marqué garde son premier instant : c'est le début
    /// d'une étape qui se date, et un écran peut repasser par le même état.
    void marquer(String nom, long ns) {
        if (jalons.stream().noneMatch(jalon -> jalon.nom().equals(nom))) {
            jalons.add(new Jalon(nom, ns));
        }
    }

    /// Les jalons dans l'ordre où ils sont arrivés, chacun à sa distance du premier.
    String jalons() {
        return jalons.stream()
                .map(jalon -> jalon.nom() + " " + depuisLeDebut(jalon.ns()))
                .collect(Collectors.joining(" · "));
    }

    /// Où tombe `ecart` : après quel jalon, sur lesquels, avant lequel.
    String situer(EcartDate ecart) {
        List<String> parties = new ArrayList<>();
        jalons.stream()
                .filter(jalon -> jalon.ns() <= ecart.debutNs())
                .reduce((premier, dernier) -> dernier)
                .ifPresent(avant -> parties.add("après " + avant.entreGuillemets()));
        String contenus = jalons.stream()
                .filter(jalon -> jalon.ns() > ecart.debutNs() && jalon.ns() <= ecart.finNs())
                .map(Jalon::entreGuillemets)
                .collect(Collectors.joining(" et "));
        if (!contenus.isEmpty()) {
            parties.add("contient " + contenus);
        }
        jalons.stream()
                .filter(jalon -> jalon.ns() > ecart.finNs())
                .findFirst()
                .ifPresent(apres -> parties.add("avant " + apres.entreGuillemets()));
        return String.join(", ", parties);
    }

    /// La ligne d'un écart dans le relevé : sa durée, ses deux instants, et où il tombe.
    String ligne(EcartDate ecart) {
        return String.format(
                Locale.ROOT,
                "%.0f ms, de %s à %s : %s",
                ecart.ms(),
                depuisLeDebut(ecart.debutNs()),
                depuisLeDebut(ecart.finNs()),
                situer(ecart));
    }

    private String depuisLeDebut(long ns) {
        long origine = jalons.isEmpty() ? ns : jalons.getFirst().ns();
        return String.format(Locale.ROOT, "%+.3f s", (ns - origine) / 1e9);
    }
}
