package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.TaxonVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Synchronise le référentiel **taxons** local avec VigieChiro (#728 puis #717, axe 2) à la connexion,
/// en deux temps depuis `GET /taxons/liste` :
/// 1. **Fusion conservatrice** du référentiel officiel dans la table `taxon` ([TaxonDao#fusionnerReferentielOfficiel])
///    : ajoute les taxons officiels absents du seed et complète les noms latins manquants, sans jamais
///    écraser les noms curés (le seed V05 reste le repli hors-ligne, seule source des noms français) ;
/// 2. **Rapprochement** `code -> objectid` de tous les taxons officiels dans `vigiechiro_link`
///    (prérequis des corrections et du dépôt).
///
/// Contribué au `Multibinder<RapprochementVigieChiro>` par `ValidationModule` ; invoqué à la connexion
/// par `connexion`. Ne dépend que du [TaxonDao] de sa feature et du [LienVigieChiroDao] du socle : le
/// client est reçu **en argument**, jamais injecté (les injecteurs autonomes restent valides).
public class RapprochementTaxons implements RapprochementVigieChiro {

    private static final Logger LOG = Logger.getLogger(RapprochementTaxons.class.getName());

    /// Libellé du compte-rendu (pluriel, cf. RapportSynchro#libelle).
    private static final String LIBELLE_TAXONS = "taxons";

    private final TaxonDao taxonDao;
    private final LienVigieChiroDao liens;

    public RapprochementTaxons(TaxonDao taxonDao, LienVigieChiroDao liens) {
        this.taxonDao = Objects.requireNonNull(taxonDao, "taxonDao");
        this.liens = Objects.requireNonNull(liens, "liens");
    }

    @Override
    public Optional<RapportSynchro> synchroniser(ClientVigieChiro client) {
        try {
            // Toute issue non-succès = ne toucher ni la table ni les liens (garde anti-purge : un
            // incident transitoire ne doit pas altérer un référentiel déjà acquis). Depuis #1284 la
            // cause remonte au bandeau, au lieu d'être omise en silence — sauf « non connecté »,
            // silence légitime du hors-ligne.
            return switch (client.taxons()) {
                case ReponseApi.Succes<List<TaxonVigieChiro>>(List<TaxonVigieChiro> officiels) -> fusionner(officiels);
                case ReponseApi.NonConnecte<List<TaxonVigieChiro>> nonConnecte -> Optional.empty();
                case ReponseApi.Injoignable<List<TaxonVigieChiro>>(String cause) ->
                    Optional.of(RapportSynchro.empechee(LIBELLE_TAXONS, "VigieChiro injoignable : " + cause));
                case ReponseApi.Refuse<List<TaxonVigieChiro>>(int statut, String corps) ->
                    Optional.of(RapportSynchro.empechee(LIBELLE_TAXONS, "refus HTTP " + statut));
            };
        } catch (RuntimeException echec) {
            LOG.log(Level.FINE, echec, () -> "Synchronisation des taxons VigieChiro ignorée (best-effort)");
            return Optional.empty();
        }
    }

    /// Fusion + rapprochement d'un référentiel effectivement reçu. Une liste vide (référentiel distant
    /// sans contenu, jamais observé en pratique) reste un no-op prudent.
    private Optional<RapportSynchro> fusionner(List<TaxonVigieChiro> officiels) {
        if (officiels.isEmpty()) {
            return Optional.empty();
        }
        {
            // 1. Fusion conservatrice du référentiel officiel dans la table `taxon`.
            Map<String, String> codeVersNomLatin = new LinkedHashMap<>();
            for (TaxonVigieChiro taxon : officiels) {
                codeVersNomLatin.put(taxon.libelleCourt(), taxon.libelleLong());
            }
            taxonDao.fusionnerReferentielOfficiel(codeVersNomLatin);
            // 2. Rapprochement code -> objectid de tous les taxons officiels (tous présents localement après
            //    la fusion). remplacer() reflète l'état courant et purge les correspondances obsolètes.
            //    Les taxons n'ont pas d'état « verrouillé » (spécifique aux sites) : constructeur 3-arg.
            List<LienVigieChiro> liensParCode = new ArrayList<>();
            for (TaxonVigieChiro taxon : officiels) {
                liensParCode.add(new LienVigieChiro(LienVigieChiro.ENTITE_TAXON, taxon.libelleCourt(), taxon.id()));
            }
            liens.remplacer(LienVigieChiro.ENTITE_TAXON, liensParCode);
            return Optional.of(new RapportSynchro(LIBELLE_TAXONS, officiels.size()));
        }
    }
}
