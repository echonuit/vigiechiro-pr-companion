package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/// **Importe et relie** les sites de l'observateur VigieChiro à la connexion (#728/#718).
///
/// Les sites d'un observateur viennent de `GET /moi/participations` (cf. [ClientVigieChiro#mesSites()]) :
/// un observateur *participe* à des sites régionaux dont il n'est pas propriétaire. Chaque site distant
/// porte son **numéro de carré** (extrait du titre) et ses **points** (localités). Pour chacun :
/// - si un site local **de même carré** existe → on le **relie** à son `objectid` ;
/// - sinon on le **crée** (site + points, via [ServiceSites]) puis on le relie.
///
/// Un site atteint par une participation est **verrouillé** (dépôt possible) → le lien porte
/// `verrouille=true` (badge « Verrouillé » sur M-Sites). Idempotent : la déduplication par carré évite
/// tout doublon aux connexions suivantes. **Best-effort** : l'échec d'un site (ou d'un point) est logué
/// et ignoré, sans compromettre les autres ni la connexion.
///
/// Contribué au `Multibinder<RapprochementVigieChiro>` par `SitesModule` ; le client est reçu **en
/// argument** (aucune dépendance vers la feature `connexion`).
public class RapprochementSites implements RapprochementVigieChiro {

    private static final Logger LOG = Logger.getLogger(RapprochementSites.class.getName());

    /// Libellé du compte-rendu (pluriel, cf. RapportSynchro#libelle).
    private static final String LIBELLE_SITES = "sites";

    /// Les liens entre un site local et son homologue distant : c'est par eux qu'on sait ce qui est
    /// déjà rapproché, et donc ce qui reste à importer.
    private final LienVigieChiroDao liens;

    /// La mécanique d'import d'un site distant, partagée avec le rapatriement à la demande (#3806).
    private final ImportSiteDistant imports;

    public RapprochementSites(LienVigieChiroDao liens, ImportSiteDistant imports) {
        this.liens = Objects.requireNonNull(liens, "liens");
        this.imports = Objects.requireNonNull(imports, "imports");
    }

    @Override
    public Optional<RapportSynchro> synchroniser(ClientVigieChiro client) {
        try {
            // Toute issue non-succès = ne toucher à rien (ni création, ni purge : garde anti-purge).
            // Depuis #1284 la cause remonte, au lieu d'être omise en silence : sauf « non connecté ».
            return switch (client.mesSites()) {
                case ReponseApi.Succes<List<SiteVigieChiro>>(List<SiteVigieChiro> distants) ->
                    importer(distants, idProfilConnecte(client));
                case ReponseApi.NonConnecte<List<SiteVigieChiro>> nonConnecte -> Optional.empty();
                case ReponseApi.Injoignable<List<SiteVigieChiro>>(String cause) ->
                    Optional.of(RapportSynchro.empechee(LIBELLE_SITES, "Vigie-Chiro injoignable : " + cause));
                case ReponseApi.Refuse<List<SiteVigieChiro>>(int statut, String corps) ->
                    Optional.of(RapportSynchro.empechee(LIBELLE_SITES, "refus HTTP " + statut));
            };
        } catch (RuntimeException echec) {
            LOG.log(Level.FINE, echec, () -> "Import des sites Vigie-Chiro ignoré (best-effort)");
            return Optional.empty();
        }
    }

    /// Identifiant plateforme de l'observateur connecté (`GET /moi`), ou `null` si le profil n'a pas pu
    /// être lu. On le demande **au client déjà fourni** plutôt qu'à la feature `connexion` (dont cette
    /// classe ne dépend pas, cf. note d'en-tête). Sans profil, aucun carré n'est présumé « d'un tiers » :
    /// le rapprochement se comporte comme avant #2525.
    private static String idProfilConnecte(ClientVigieChiro client) {
        return client.moi() instanceof ReponseApi.Succes<ProfilVigieChiro>(ProfilVigieChiro profil)
                ? profil.id()
                : null;
    }

    /// Import/liaison de sites effectivement reçus. Une liste vide (observateur sans participation)
    /// reste un no-op prudent.
    ///
    /// La mécanique d'import - créer, compléter, poser les points rapatriés, marquer la propriété - vit
    /// dans [ImportSiteDistant] depuis #3806 : le rapatriement **à la demande** d'un carré doit poser
    /// exactement le même état que cette synchronisation périodique.
    private Optional<RapportSynchro> importer(List<SiteVigieChiro> distants, String idProfilConnecte) {
        if (distants.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Site> localesParCarre = imports.sitesLocauxParCarre();
        List<LienVigieChiro> correspondances = new ArrayList<>();
        for (SiteVigieChiro distant : distants) {
            imports.importerOuLier(distant, localesParCarre, idProfilConnecte)
                    .map(ImportSiteDistant.ResultatImport::lien)
                    .ifPresent(correspondances::add);
        }
        if (correspondances.isEmpty()) {
            return Optional.empty();
        }
        // `remplacer` et non `upsert` : cette synchronisation connaît TOUS les sites de l'observateur,
        // elle a donc autorité pour purger les correspondances qu'elle ne cite plus. Le rapatriement à la
        // demande, lui, n'en connaît qu'un et se contente d'un `upsert` (#3806).
        liens.remplacer(LienVigieChiro.ENTITE_SITE, correspondances);
        imports.rattraperCommunes();
        return Optional.of(new RapportSynchro(LIBELLE_SITES, correspondances.size()));
    }
}
