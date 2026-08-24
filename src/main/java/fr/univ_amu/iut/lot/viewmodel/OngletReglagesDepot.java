package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.view.DescripteurReglage;
import fr.univ_amu.iut.commun.view.OngletReglages;
import fr.univ_amu.iut.lot.model.ModeDepot;
import java.util.List;

/// Onglet « Dépôt » de l’écran Réglages (#1047) : **forme du dépôt** (archives ZIP ou séquences WAV,
/// #1997) et plafond de taille des archives, contribués par le module `lot`.
///
/// Le réglage pilote le [fr.univ_amu.iut.lot.model.CompacteurDepot] via `LotModule` : relu à chaque
/// **génération d’archives** (pas de redémarrage nécessaire). La propriété système
/// `vigiechiro.depot.taille-max-mo` reste prioritaire (tests/outils). Le défaut et la borne haute
/// sont la contrainte de la plateforme (700 Mo, base 1000).
public final class OngletReglagesDepot implements OngletReglages {

    /// Clé du réglage persisté (table `app_setting`).
    public static final String CLE_TAILLE_MAX = "depot.taille-max-mo";

    /// Clé du **mode de dépôt** (#1997) : archives ZIP ou séquences WAV. Voir
    /// [fr.univ_amu.iut.lot.model.ModeDepot] pour ce que ce choix engage.
    public static final String CLE_MODE_DEPOT = "depot.mode";

    /// Défaut (et borne haute) : contrainte Tadarida côté plateforme.
    public static final int DEFAUT_TAILLE_MAX_MO = 700;

    static final int MIN_TAILLE_MAX_MO = 50;
    static final int MAX_TAILLE_MAX_MO = DEFAUT_TAILLE_MAX_MO;

    @Override
    public String idFeature() {
        return "lot";
    }

    @Override
    public int ordre() {
        return 40;
    }

    @Override
    public String titre() {
        return "Dépôt";
    }

    @Override
    public String iconeLiteral() {
        return "fas-cloud-upload-alt";
    }

    @Override
    public List<DescripteurReglage> reglages() {
        return List.of(
                new DescripteurReglage.Enumeration(
                        CLE_MODE_DEPOT,
                        "Forme du dépôt",
                        "En archives ZIP, la plateforme extrait puis supprime l'archive sans conserver les"
                                + " sons : l'audio n'est plus téléchargeable depuis Vigie-Chiro, et la"
                                + " participation ne pourra pas être relancée. En séquences WAV, chaque son"
                                + " reste en ligne et la participation reste relançable, au prix d'un dépôt"
                                + " plus long.",
                        List.of(
                                new DescripteurReglage.Enumeration.Option(
                                        ModeDepot.ARCHIVES_ZIP.valeur(), ModeDepot.ARCHIVES_ZIP.libelle()),
                                new DescripteurReglage.Enumeration.Option(
                                        ModeDepot.SEQUENCES_WAV.valeur(), ModeDepot.SEQUENCES_WAV.libelle())),
                        ModeDepot.ARCHIVES_ZIP.valeur()),
                new DescripteurReglage.Entier(
                        CLE_TAILLE_MAX,
                        "Taille maximale d'une archive (Mo)",
                        "Plafond de découpe des archives ZIP générées pour le dépôt (base 1000 : 700 Mo"
                                + " = 700 000 000 octets, la limite acceptée par la plateforme). Appliqué à la"
                                + " prochaine génération d'archives.",
                        DEFAUT_TAILLE_MAX_MO,
                        MIN_TAILLE_MAX_MO,
                        MAX_TAILLE_MAX_MO));
    }
}
