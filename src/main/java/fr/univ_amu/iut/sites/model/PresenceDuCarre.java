package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// **Sous quelle forme un carré est présent sur Vigie-Chiro**, d'après les sites que la recherche a rendus
/// (#5607). Seul le Point Fixe reçoit les nuits que Companion dépose.
///
/// La vérification, le rapatriement et `creer-site` classaient ce même résultat chacun à sa façon, et
/// seul le rapatriement distinguait le Point Fixe. Chaque geste garde sa phrase ; le classement, lui,
/// s'écrit ici une fois.
public sealed interface PresenceDuCarre {

    /// Ce qu'il faudra faire, avant de déposer, d'un carré absent ou présent sous un autre protocole. Une
    /// seule phrase, que lisent la vérification, le rapatriement et `creer-site`.
    String GESTE_DU_PORTAIL = "pour y déposer des nuits, il faudra l'activer en Point Fixe sur le portail"
            + " Vigie-Chiro (y créer un point), puis le récupérer ici.";

    /// Le carré est déclaré en Point Fixe : c'est ce site qu'un rapatriement rattache.
    record PointFixe(SiteVigieChiro site) implements PresenceDuCarre {

        public PointFixe {
            Objects.requireNonNull(site, "site");
        }
    }

    /// Le carré existe, mais sous un autre protocole seulement : Routier, Pédestre.
    ///
    /// @param titres les sites trouvés, dont le titre nomme le protocole
    record AutreProtocole(List<String> titres) implements PresenceDuCarre {

        public AutreProtocole {
            titres = List.copyOf(titres);
        }
    }

    /// Aucun site ne porte ce carré.
    record Absent() implements PresenceDuCarre {}

    /// Classe les sites rendus par la recherche d'un carré.
    static PresenceDuCarre de(List<SiteVigieChiro> trouves) {
        Objects.requireNonNull(trouves, "trouves");
        if (trouves.isEmpty()) {
            return new Absent();
        }
        Optional<SiteVigieChiro> pointFixe =
                trouves.stream().filter(SiteVigieChiro::estPointFixe).findFirst();
        return pointFixe
                .<PresenceDuCarre>map(PointFixe::new)
                .orElseGet(() -> new AutreProtocole(trouves.stream()
                        .map(SiteVigieChiro::titre)
                        .filter(Objects::nonNull)
                        .toList()));
    }
}
