package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.view.DescripteurReglage;
import fr.univ_amu.iut.commun.view.OngletReglages;
import fr.univ_amu.iut.importation.model.ReglageConservationOriginaux;
import java.util.List;

/// Onglet « Import » de l'écran Réglages (#928) : préférences du parcours d'import, contribué par le
/// module `importation`.
///
/// Cet onglet est le **seul propriétaire** du réglage « Conserver les originaux »
/// ([ReglageConservationOriginaux]) : il le persiste immédiatement, et l'écran d'import se contente de
/// le **lire** au moment de s'en servir.
///
/// Il y avait auparavant **deux écrivains sur la même clé, à deux temporalités** : l'onglet écrivait
/// tout de suite, l'écran d'import avait une « sémantique différée » qui mémorisait au lancement. La
/// case ayant déménagé ici, cette écriture différée ne portait plus aucun choix d'utilisateur : elle
/// rejouait un instantané pris au démarrage et **écrasait** ce qu'on venait de régler (#3471).
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
                ReglageConservationOriginaux.CLE,
                "Conserver les originaux pour ré-analyse ultérieure",
                "Copie les WAV bruts dans bruts/ avant transformation. Utile si vous comptez ré-analyser vos"
                        + " enregistrements avec d'autres réglages : l'application n'en a pas besoin, et vos"
                        + " fichiers d'origine ne sont jamais modifiés. Coûte plusieurs Go par nuit et rend"
                        + " l'import environ trois fois plus long (désactivé : transformation directe depuis"
                        + " la source).",
                ReglageConservationOriginaux.DEFAUT));
    }
}
