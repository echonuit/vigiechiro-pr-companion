package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Severite;
import java.util.List;
import java.util.Locale;
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

        /// Le carré existe, mais **sous un protocole que l'application ne gère pas**.
        ///
        /// Un numéro de carré ne désigne pas un site : le même carré peut exister en Point Fixe, en
        /// Pédestre et en Routier. Companion ne traite que le **Point Fixe** (les autres n'ont pas de
        /// nuits complètes à importer), et rattacher un carré Routier enverrait la nuit au mauvais
        /// endroit. Dire « inexistant » serait faux, et se taire laisserait l'utilisateur devant un
        /// numéro qui « existe » sans être récupérable.
        ///
        /// @param titres les sites trouvés, dont le titre nomme leur protocole
        record AutreProtocole(List<String> titres) implements Resultat {
            @Override
            public String message() {
                return "Ce carré existe sur Vigie-Chiro, mais pas en Point Fixe (" + String.join(", ", titres)
                        + "). Companion ne gère que le Point Fixe : rien n'a été récupéré.";
            }

            @Override
            public Severite severite() {
                return Severite.AVERTISSEMENT;
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
    public Resultat rapatrier(String numeroCarre, Protocole protocole) {
        Objects.requireNonNull(numeroCarre, "numeroCarre");
        Objects.requireNonNull(protocole, "protocole");
        return switch (client.chercherCarre(numeroCarre)) {
            case ReponseApi.Succes<List<SiteVigieChiro>>(List<SiteVigieChiro> trouves) ->
                poser(numeroCarre, trouves, protocole);
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

    /// Pose le site **en Point Fixe** parmi ceux trouvés. Un carré peut en porter plusieurs, un par
    /// protocole : prendre le premier venu rattacherait au hasard, et la nuit partirait au mauvais
    /// endroit.
    private Resultat poser(String numeroCarre, List<SiteVigieChiro> trouves, Protocole protocole) {
        if (trouves.isEmpty()) {
            return new Resultat.Inexistant(numeroCarre);
        }
        Optional<SiteVigieChiro> pointFixe =
                trouves.stream().filter(RapatriementCarre::estPointFixe).findFirst();
        if (pointFixe.isEmpty()) {
            return new Resultat.AutreProtocole(trouves.stream()
                    .map(SiteVigieChiro::titre)
                    .filter(Objects::nonNull)
                    .toList());
        }
        SiteVigieChiro distant = pointFixe.get();
        Map<String, Site> locauxParCarre = imports.sitesLocauxParCarre();
        Optional<ImportSiteDistant.ResultatImport> importe =
                imports.importerOuLier(distant, locauxParCarre, idProfilConnecte(), protocole);
        if (importe.isEmpty()) {
            return new Resultat.Indisponible();
        }
        imports.enregistrer(importe.get().lien());
        imports.rattraperCommunes();
        // Le compte vient de l'import, pas de la réponse : un point refusé en best-effort ne doit pas
        // être annoncé comme posé.
        return imports.siteLocalDuCarre(numeroCarre)
                .<Resultat>map(site -> new Resultat.Rapatrie(site, importe.get().pointsPoses()))
                .orElseGet(Resultat.Indisponible::new);
    }

    /// Le titre d'un site nomme son protocole plateforme (`Vigiechiro - Point Fixe-130711`,
    /// `Vigie-chiro - Routier-…`). C'est la seule marque disponible : la recherche ne rend pas le
    /// protocole en clair, et le résoudre coûterait une requête de plus par site.
    ///
    /// ⚠️ **`PointFixeStandard` et `PointFixeRecherche` sont tous deux du Point Fixe** : ce sont des
    /// variantes **locales** (R3/R4 muettes ou non), pas deux protocoles de la plateforme. Le filtre
    /// porte donc sur la famille, jamais sur la variante choisie par l'utilisateur.
    private static boolean estPointFixe(SiteVigieChiro site) {
        return site.titre() != null && site.titre().toLowerCase(Locale.FRENCH).contains("point fixe");
    }

    /// L'identifiant du profil connecté, pour savoir si le carré appartient à un tiers (#2525). Sans lui,
    /// `appartientAUnTiers` répond faux : on ne présume pas un tiers sans preuve.
    private String idProfilConnecte() {
        return client.moi() instanceof ReponseApi.Succes<ProfilVigieChiro>(ProfilVigieChiro profil)
                ? profil.id()
                : null;
    }
}
