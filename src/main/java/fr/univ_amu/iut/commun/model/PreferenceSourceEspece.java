package fr.univ_amu.iut.commun.model;

import com.google.inject.Inject;
import java.util.Objects;

/// Préférence utilisateur **« source des fiches espèces »** (#849), persistée dans les réglages
/// applicatifs (`app_setting`, via [Reglages]) : deux choix pour la source universelle (celle qui sert
/// aux taxons hors PNA), **GBIF** (défaut) ou **Wikipédia FR**.
///
/// Le PNA reste toujours prioritaire pour les chiroptères (cf. [ConstructeurLienEspece]) ; cette
/// préférence ne concerne que le repli universel. Lue à chaque construction de lien, elle prend effet
/// **sans redémarrage**. Défaut : GBIF (comportement historique), donc aucune initialisation préalable.
public final class PreferenceSourceEspece {

    /// Clé du réglage persisté (cf. [Reglages]).
    static final String CLE = "espece.source.wikipedia";

    private final Reglages reglages;

    @Inject
    public PreferenceSourceEspece(Reglages reglages) {
        this.reglages = Objects.requireNonNull(reglages, "reglages");
    }

    /// `true` si l'utilisateur préfère **Wikipédia FR** comme source universelle ; `false` (défaut) pour
    /// **GBIF**.
    public boolean prefereWikipedia() {
        return reglages.lireBooleen(CLE, false);
    }

    /// Mémorise le choix de source universelle (`true` = Wikipédia FR, `false` = GBIF).
    public void definirPrefereWikipedia(boolean prefereWikipedia) {
        reglages.ecrireBooleen(CLE, prefereWikipedia);
    }
}
