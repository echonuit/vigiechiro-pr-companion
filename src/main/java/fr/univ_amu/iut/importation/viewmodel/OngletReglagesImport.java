package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.view.DescripteurReglage;
import fr.univ_amu.iut.commun.view.OngletReglages;
import java.util.List;

/// Onglet « Import » de l'écran Réglages (#928) : préférences du parcours d'import, contribué par le
/// module `importation`.
///
/// Le descripteur pointe la **même clé** que la case « Conserver les originaux » de l'écran d'import
/// ([PreferenceConservation#CLE]) : c'est le même réglage persistant. L'écran d'import garde sa
/// sémantique différée (mémorisation au lancement de l'import) ; l'onglet, lui, persiste
/// immédiatement.
public final class OngletReglagesImport implements OngletReglages {

    @Override
    public String idFeature() {
        return "importation";
    }

    @Override
    public int ordre() {
        return 20;
    }

    @Override
    public String titre() {
        return "Import";
    }

    @Override
    public String iconeLiteral() {
        return "fas-file-import";
    }

    @Override
    public List<DescripteurReglage> reglages() {
        return List.of(new DescripteurReglage.Booleen(
                PreferenceConservation.CLE,
                "Conserver les originaux pour ré-analyse ultérieure",
                "Copie les WAV bruts dans bruts/ avant transformation. Utile si vous comptez ré-analyser vos"
                        + " enregistrements avec d'autres réglages : l'application n'en a pas besoin, et vos"
                        + " fichiers d'origine ne sont jamais modifiés. Coûte plusieurs Go par nuit et rend"
                        + " l'import environ trois fois plus long (désactivé : transformation directe depuis"
                        + " la source).",
                false));
    }
}
