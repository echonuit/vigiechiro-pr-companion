package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.api.TaxonVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final TaxonDao taxonDao;
    private final LienVigieChiroDao liens;

    public RapprochementTaxons(TaxonDao taxonDao, LienVigieChiroDao liens) {
        this.taxonDao = Objects.requireNonNull(taxonDao, "taxonDao");
        this.liens = Objects.requireNonNull(liens, "liens");
    }

    @Override
    public void synchroniser(ClientVigieChiro client) {
        try {
            List<TaxonVigieChiro> officiels = client.taxons();
            // Liste vide = non connecté / API indisponible : on ne touche ni la table ni les liens (un
            // incident réseau transitoire ne doit pas altérer un référentiel déjà acquis).
            if (officiels.isEmpty()) {
                return;
            }
            // 1. Fusion conservatrice du référentiel officiel dans la table `taxon`.
            Map<String, String> codeVersNomLatin = new LinkedHashMap<>();
            for (TaxonVigieChiro taxon : officiels) {
                codeVersNomLatin.put(taxon.libelleCourt(), taxon.libelleLong());
            }
            taxonDao.fusionnerReferentielOfficiel(codeVersNomLatin);
            // 2. Rapprochement code -> objectid de tous les taxons officiels (tous présents localement après
            //    la fusion). remplacer() reflète l'état courant et purge les correspondances obsolètes.
            Map<String, String> liensParCode = new LinkedHashMap<>();
            for (TaxonVigieChiro taxon : officiels) {
                liensParCode.put(taxon.libelleCourt(), taxon.id());
            }
            liens.remplacer(LienVigieChiro.ENTITE_TAXON, liensParCode);
        } catch (RuntimeException echec) {
            LOG.log(Level.FINE, echec, () -> "Synchronisation des taxons VigieChiro ignorée (best-effort)");
        }
    }
}
