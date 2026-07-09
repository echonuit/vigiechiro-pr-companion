package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Rapproche les **sites** locaux avec ceux de l'observateur sur VigieChiro (#728, axe 1) : appelle
/// `GET /moi/sites` et relie chaque site local à son `objectid`.
///
/// Le rapprochement est **best-effort par le titre** (la plateforme n'expose pas directement le numéro
/// de carré) : un site local correspond au premier site VigieChiro dont le `titre` contient son numéro
/// de carré, ou dont le `titre` égale (à la casse près) son nom convivial. Faute de site VigieChiro sur
/// le compte de test, l'heuristique n'a pu être validée en réel ; elle est couverte par tests unitaires.
///
/// Contribué au `Multibinder<RapprochementVigieChiro>` par `SitesModule`. Ne dépend que du [SiteDao] de
/// sa feature et du [LienVigieChiroDao] du socle ; le client est reçu **en argument**.
public class RapprochementSites implements RapprochementVigieChiro {

    private static final Logger LOG = Logger.getLogger(RapprochementSites.class.getName());

    private final SiteDao siteDao;
    private final LienVigieChiroDao liens;

    public RapprochementSites(SiteDao siteDao, LienVigieChiroDao liens) {
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.liens = Objects.requireNonNull(liens, "liens");
    }

    @Override
    public Optional<RapportSynchro> synchroniser(ClientVigieChiro client) {
        try {
            List<SiteVigieChiro> distants = client.mesSites();
            Map<String, String> correspondances = new LinkedHashMap<>();
            for (Site local : siteDao.findAll()) {
                correspondant(local, distants)
                        .ifPresent(distant -> correspondances.put(String.valueOf(local.id()), distant.id()));
            }
            // Aucune correspondance = non connecté, aucun site distant, ou aucun titre rapproché : on ne
            // purge pas les liens déjà acquis (prudence face à une heuristique de titre imparfaite).
            if (correspondances.isEmpty()) {
                return Optional.empty();
            }
            liens.remplacer(LienVigieChiro.ENTITE_SITE, correspondances);
            return Optional.of(new RapportSynchro("sites", correspondances.size()));
        } catch (RuntimeException echec) {
            LOG.log(Level.FINE, echec, () -> "Rapprochement des sites VigieChiro ignoré (best-effort)");
            return Optional.empty();
        }
    }

    /// Premier site VigieChiro correspondant au site `local` parmi `distants`, ou vide.
    static Optional<SiteVigieChiro> correspondant(Site local, List<SiteVigieChiro> distants) {
        return distants.stream().filter(distant -> correspond(local, distant)).findFirst();
    }

    /// Vrai si le `titre` du site distant contient le numéro de carré du site local, ou égale (à la
    /// casse près) son nom convivial.
    static boolean correspond(Site local, SiteVigieChiro distant) {
        String titre = distant.titre() == null ? "" : distant.titre().strip();
        if (titre.isEmpty()) {
            return false;
        }
        String carre = local.numeroCarre() == null ? "" : local.numeroCarre().strip();
        if (!carre.isEmpty() && titre.contains(carre)) {
            return true;
        }
        String nom = local.nomConvivial();
        return nom != null && !nom.isBlank() && titre.equalsIgnoreCase(nom.strip());
    }
}
