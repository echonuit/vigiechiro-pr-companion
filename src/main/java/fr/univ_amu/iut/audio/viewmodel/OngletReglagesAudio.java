package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.commun.view.DescripteurReglage;
import fr.univ_amu.iut.commun.view.OngletReglages;
import java.util.List;

/// Onglet « Audio » de l'écran Réglages (#1006) : préférences de **lecture** de la vue audio,
/// contribué par le module `audio`.
///
/// Ces réglages étaient jusqu'ici des cases **volatiles** du menu ☰ de la vue audio (perdues d'une
/// session à l'autre). Les clés/défauts sont désormais **partagés** avec ces cases (cf. `LecteurAudio`) :
/// liées à la même Property réactive, le menu et l'onglet restent synchronisés, et le choix persiste.
public final class OngletReglagesAudio implements OngletReglages {

    /// Démarrer la lecture dès qu'une séquence sélectionnée est prête (défaut : oui).
    public static final String CLE_LECTURE_AUTO = "audio.lecture-auto";

    public static final boolean DEFAUT_LECTURE_AUTO = true;

    /// Répéter la séquence en cours de lecture (défaut : non).
    public static final String CLE_BOUCLE = "audio.boucle";

    public static final boolean DEFAUT_BOUCLE = false;

    /// Spectrogramme en **palette adaptée au daltonisme** (défaut : non).
    public static final String CLE_DALTONIEN = "audio.daltonien";

    public static final boolean DEFAUT_DALTONIEN = false;

    /// Inclure la colonne « mode de validation » dans l'export _Vu (défaut : oui).
    public static final String CLE_INCLURE_MODE = "audio.export.inclure-mode";

    public static final boolean DEFAUT_INCLURE_MODE = true;

    @Override
    public String idFeature() {
        return "audio";
    }

    @Override
    public int ordre() {
        return 30;
    }

    @Override
    public String titre() {
        return "Audio";
    }

    @Override
    public String iconeLiteral() {
        return "fas-volume-up";
    }

    @Override
    public List<DescripteurReglage> reglages() {
        return List.of(
                new DescripteurReglage.Booleen(
                        CLE_LECTURE_AUTO,
                        "Lecture automatique à la sélection",
                        "Démarre la lecture dès qu'une séquence sélectionnée est prête.",
                        DEFAUT_LECTURE_AUTO),
                new DescripteurReglage.Booleen(
                        CLE_BOUCLE, "Lecture en boucle", "Répète la séquence en cours de lecture.", DEFAUT_BOUCLE),
                new DescripteurReglage.Booleen(
                        CLE_DALTONIEN,
                        "Spectrogramme adapté au daltonisme",
                        "Palette de couleurs accessible pour le spectrogramme.",
                        DEFAUT_DALTONIEN),
                new DescripteurReglage.Booleen(
                        CLE_INCLURE_MODE,
                        "Inclure le mode de validation à l'export _Vu",
                        "Ajoute une colonne « mode de validation » dans l'export CSV _Vu.",
                        DEFAUT_INCLURE_MODE));
    }
}
