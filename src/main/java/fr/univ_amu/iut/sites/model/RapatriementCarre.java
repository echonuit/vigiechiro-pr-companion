package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Severite;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/// **Rapatrier un carré par son numéro** (#3806) : le créer localement, rattaché, avec ses localités
/// positionnées - sans l'avoir déposé ni le posséder.
///
/// ## Le cercle que ce service casse
///
/// Préparer une nuit *opportuniste* commence par déclarer le carré et son point. Mais le dépôt exige
/// que le site local porte un **lien** vers son homologue plateforme
/// (`SynchronisationParticipation#creerPour`, sinon « Site non rattaché à Vigie-Chiro », #3463), et la
/// synchronisation qui pose ce lien part de `GET /moi/participations` : elle ne voit que les carrés où
/// une nuit est **déjà** déposée.
///
/// > Déposer était la seule chose qui aurait créé la participation qui aurait rendu le dépôt possible.
///
/// Mesuré le 2026-08-15 : `/moi/sites` rend **0** pour un compte qui dépose depuis des mois, son carré
/// appartenant à quelqu'un d'autre. Élargir la synchronisation à cette route n'aurait donc servi
/// personne ou presque - les possesseurs de carrés sont rares, la plupart des observateurs déposent sur
/// le carré d'un tiers. `GET /sites?q=<carré>` le rend, lui, à qui le demande.
///
/// **Bloquant** (réseau) : à appeler hors du fil JavaFX.
public class RapatriementCarre {

    private static final Logger LOG = Logger.getLogger(RapatriementCarre.class.getName());

    private final ClientVigieChiro client;
    private final ImportSiteDistant imports;

    public RapatriementCarre(ClientVigieChiro client, ImportSiteDistant importSiteDistant) {
        this.client = Objects.requireNonNull(client, "client");
        this.imports = Objects.requireNonNull(importSiteDistant, "importSiteDistant");
    }

    /// Ce que le rapatriement a donné, avec son message et sa gravité - la vue les rend, elle ne les
    /// compose pas.
    public sealed interface Resultat {

        String message();

        Severite severite();

        /// Le carré est là, rattaché, avec ses points.
        ///
        /// @param site le site local, créé ou complété
        /// @param points combien de localités ont été posées
        record Rapatrie(Site site, int points) implements Resultat {

            /// Le message dit ce qui est **utilisable maintenant** : le carré est rattaché (donc le dépôt
            /// passera) et ses points sont positionnés (donc il n'y a rien à ressaisir).
            @Override
            public String message() {
                return "Carré " + site.numeroCarre() + " récupéré depuis Vigie-Chiro : " + points
                        + " point(s) d'écoute positionné(s). Vous pouvez y rattacher une nuit et la déposer.";
            }

            @Override
            public Severite severite() {
                return Severite.SUCCES;
            }
        }

        /// Aucun site ne porte ce carré sur la plateforme : il n'y a rien à rapatrier.
        record Inexistant(String numeroCarre) implements Resultat {
            @Override
            public String message() {
                return "Le carré " + numeroCarre + " n'existe pas sur Vigie-Chiro : déclarez-le ici, puis"
                        + " activez-le sur le portail avant de déposer.";
            }

            @Override
            public Severite severite() {
                return Severite.INFO;
            }
        }

        /// On n'a pas pu demander : hors connexion, plateforme injoignable, ou refus.
        ///
        /// ⚠️ **Rien n'a été créé**, et surtout rien ne laisse croire que le carré a été récupéré.
        record Indisponible() implements Resultat {
            @Override
            public String message() {
                return "Récupération impossible : Vigie-Chiro est injoignable ou vous n'êtes pas connecté."
                        + " Rien n'a été créé.";
            }

            @Override
            public Severite severite() {
                return Severite.AVERTISSEMENT;
            }
        }
    }

    /// Cherche `numeroCarre` sur la plateforme et le pose localement, rattaché.
    ///
    /// Toute issue non-succès rend [Resultat.Indisponible] **sans rien écrire** : un rapatriement à
    /// moitié fait serait pire que pas de rapatriement du tout, puisqu'il laisserait un site local non
    /// rattaché - exactement l'état que ce service existe pour éviter.
    public Resultat rapatrier(String numeroCarre) {
        Objects.requireNonNull(numeroCarre, "numeroCarre");
        return switch (client.chercherCarre(numeroCarre)) {
            case ReponseApi.Succes<List<SiteVigieChiro>>(List<SiteVigieChiro> trouves) -> poser(numeroCarre, trouves);
            case ReponseApi.NonConnecte<List<SiteVigieChiro>> nonConnecte -> new Resultat.Indisponible();
            case ReponseApi.Injoignable<List<SiteVigieChiro>>(String cause) -> {
                LOG.log(Level.FINE, () -> "Rapatriement du carré ignoré (Vigie-Chiro injoignable : " + cause + ")");
                yield new Resultat.Indisponible();
            }
            case ReponseApi.Refuse<List<SiteVigieChiro>>(int statut, String corps) -> {
                LOG.log(Level.FINE, () -> "Rapatriement du carré ignoré (refus HTTP " + statut + ")");
                yield new Resultat.Indisponible();
            }
        };
    }

    /// Pose le premier site trouvé. Un carré peut en porter plusieurs (un par protocole) ; le premier
    /// suffit à rattacher, et le titre dit lequel c'est.
    private Resultat poser(String numeroCarre, List<SiteVigieChiro> trouves) {
        if (trouves.isEmpty()) {
            return new Resultat.Inexistant(numeroCarre);
        }
        SiteVigieChiro distant = trouves.getFirst();
        Map<String, Site> locauxParCarre = imports.sitesLocauxParCarre();
        Optional<LienVigieChiro> lien = imports.importerOuLier(distant, locauxParCarre, idProfilConnecte());
        if (lien.isEmpty()) {
            return new Resultat.Indisponible();
        }
        imports.enregistrer(lien.get());
        imports.rattraperCommunes();
        return imports.siteLocalDuCarre(numeroCarre)
                .<Resultat>map(
                        site -> new Resultat.Rapatrie(site, distant.points().size()))
                .orElseGet(Resultat.Indisponible::new);
    }

    /// L'identifiant du profil connecté, pour savoir si le carré appartient à un tiers (#2525). Sans lui,
    /// `appartientAUnTiers` répond faux : on ne présume pas un tiers sans preuve.
    private String idProfilConnecte() {
        return client.moi() instanceof ReponseApi.Succes<ProfilVigieChiro>(ProfilVigieChiro profil)
                ? profil.id()
                : null;
    }
}
