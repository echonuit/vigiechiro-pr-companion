package fr.univ_amu.iut.recherche.model;

import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.RechercheGlobale;
import fr.univ_amu.iut.commun.model.ResultatRecherche;
import fr.univ_amu.iut.commun.model.TypeResultat;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.validation.model.EspeceObservee;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAnalyseDao;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Implémentation de la [RechercheGlobale] (#144) : agrège les lectures des features `sites`
/// (sites + points) et `multisite` (passages aplatis en [LignePassage]) pour l'utilisateur courant,
/// et filtre par correspondance **insensible casse/accents** ([NormalisationTexte]).
///
/// Service **model** pur (aucune dépendance JavaFX/navigation) : il renvoie des [ResultatRecherche]
/// porteurs des seules clés d'identité ; c'est la couche `view` qui décidera de l'écran à ouvrir.
/// Réutilise les **services** existants quand ils portent une agrégation à ne pas redupliquer
/// ([ServiceMultisite] pour les passages), et la **projection dédiée** [ProjectionsAnalyseDao]
/// pour les espèces observées (#1193).
public class ServiceRechercheGlobale implements RechercheGlobale {

    /// Plafond de résultats **par type** : la liste déroulante reste lisible et la recherche rapide.
    /// Au-delà, l'utilisateur affine sa saisie (les résultats supplémentaires sont silencieusement omis).
    static final int MAX_PAR_TYPE = 8;

    /// Séparateur des segments d'un libellé/détail de résultat (p. ex. `Chiroptères · 640380 / A1 · n°2`).
    private static final String SEPARATEUR = " · ";

    private final ServiceSites services;
    private final ServiceMultisite multisite;
    private final ProjectionsAnalyseDao projections;
    private final String idUtilisateur;

    public ServiceRechercheGlobale(
            ServiceSites services,
            ServiceMultisite multisite,
            ProjectionsAnalyseDao projections,
            String idUtilisateur) {
        this.services = Objects.requireNonNull(services, "services");
        this.multisite = Objects.requireNonNull(multisite, "multisite");
        this.projections = Objects.requireNonNull(projections, "projections");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
    }

    @Override
    public List<ResultatRecherche> rechercher(String requete) {
        String aiguille = NormalisationTexte.normaliser(requete);
        if (aiguille.isEmpty()) {
            return List.of();
        }
        List<ResultatRecherche> resultats = new ArrayList<>();
        resultats.addAll(chercherSitesEtPoints(aiguille));
        resultats.addAll(chercherPassages(aiguille));
        resultats.addAll(chercherEspeces(aiguille));
        return resultats;
    }

    /// Espèces observées correspondantes (par code ou nom latin / vernaculaire), **une entrée par
    /// passage** où l'espèce a été relevée (#323), plafonnée à [#MAX_PAR_TYPE]. Cliquer une entrée ouvre
    /// ce passage. La projection est déjà triée du plus récent au plus ancien : le plafond garde les plus
    /// récents.
    ///
    /// Limite connue (comme [#chercherPassages]) : la projection matérialise tous les couples
    /// (espèce, passage) de l'utilisateur avant filtrage/plafond. Suffisant aux volumes actuels.
    private List<ResultatRecherche> chercherEspeces(String aiguille) {
        List<ResultatRecherche> especes = new ArrayList<>();
        for (EspeceObservee espece : projections.especesObserveesParUtilisateur(idUtilisateur)) {
            if (especes.size() >= MAX_PAR_TYPE) {
                break;
            }
            if (correspond(aiguille, espece.code(), espece.nomLatin(), espece.nomVernaculaireFr())) {
                especes.add(resultatEspece(espece));
            }
        }
        return especes;
    }

    /// Sites correspondants (par n° de carré ou nom) puis, pour chaque site, ses points correspondants
    /// (par code ou description). Chaque catégorie est plafonnée à [#MAX_PAR_TYPE].
    private List<ResultatRecherche> chercherSitesEtPoints(String aiguille) {
        List<ResultatRecherche> sites = new ArrayList<>();
        List<ResultatRecherche> points = new ArrayList<>();
        for (Site site : services.listerSites(idUtilisateur)) {
            // Les deux plafonds atteints : inutile de continuer (et surtout d'interroger d'autres points).
            if (sites.size() >= MAX_PAR_TYPE && points.size() >= MAX_PAR_TYPE) {
                break;
            }
            if (sites.size() < MAX_PAR_TYPE && correspond(aiguille, site.numeroCarre(), site.nomConvivial())) {
                sites.add(resultatSite(site));
            }
            // N'interroge les points d'un site (requête par site) que s'il reste de la place côté points :
            // une fois le plafond de points atteint, on évite un listerPoints inutile pour chaque site.
            if (site.id() == null || points.size() >= MAX_PAR_TYPE) {
                continue;
            }
            for (PointDEcoute point : services.listerPoints(site.id())) {
                if (points.size() >= MAX_PAR_TYPE) {
                    break;
                }
                if (correspond(aiguille, point.code(), point.description())) {
                    points.add(resultatPoint(site, point));
                }
            }
        }
        List<ResultatRecherche> fusion = new ArrayList<>(sites);
        fusion.addAll(points);
        return fusion;
    }

    /// Passages correspondants (par n° de carré, code point, n° de passage, année ou date), plafonnés.
    ///
    /// Limite connue : `listerPassages` **matérialise** tous les passages de l'utilisateur avant le
    /// filtrage/plafond. Suffisant aux volumes actuels ; si le nombre de passages grandit nettement, une
    /// **projection dédiée** (requête filtrante en base) remplacera ce balayage en mémoire.
    private List<ResultatRecherche> chercherPassages(String aiguille) {
        List<ResultatRecherche> passages = new ArrayList<>();
        for (LignePassage ligne : multisite.listerPassages(idUtilisateur)) {
            if (passages.size() >= MAX_PAR_TYPE) {
                break;
            }
            boolean trouve = correspond(
                    aiguille,
                    ligne.numeroCarre(),
                    ligne.codePoint(),
                    Integer.toString(ligne.numeroPassage()),
                    Integer.toString(ligne.annee()),
                    ligne.dateEnregistrement());
            if (trouve) {
                passages.add(resultatPassage(ligne));
            }
        }
        return passages;
    }

    private static ResultatRecherche resultatSite(Site site) {
        boolean aNom = site.nomConvivial() != null && !site.nomConvivial().isBlank();
        String libelle = aNom ? site.nomConvivial() : "Carré " + site.numeroCarre();
        String details = aNom ? "Carré " + site.numeroCarre() : "Site";
        return new ResultatRecherche(
                TypeResultat.SITE, libelle, details, site.numeroCarre(), null, site.nomConvivial(), null);
    }

    /// Libellé `carré / point` (p. ex. `640380 / A1`), partagé par les résultats point/passage/espèce.
    private static String carreEtPoint(String numeroCarre, String codePoint) {
        return numeroCarre + " / " + codePoint;
    }

    private static ResultatRecherche resultatPoint(Site site, PointDEcoute point) {
        String libelle = carreEtPoint(site.numeroCarre(), point.code());
        String details =
                point.description() != null && !point.description().isBlank() ? point.description() : "Point d'écoute";
        return new ResultatRecherche(
                TypeResultat.POINT, libelle, details, site.numeroCarre(), point.code(), site.nomConvivial(), null);
    }

    private static ResultatRecherche resultatPassage(LignePassage ligne) {
        String libelle =
                carreEtPoint(ligne.numeroCarre(), ligne.codePoint()) + SEPARATEUR + "n°" + ligne.numeroPassage();
        String details = ligne.dateEnregistrement() != null
                ? "Passage " + ligne.annee() + SEPARATEUR + ligne.dateEnregistrement()
                : "Passage " + ligne.annee();
        return new ResultatRecherche(
                TypeResultat.PASSAGE,
                libelle,
                details,
                ligne.numeroCarre(),
                ligne.codePoint(),
                null,
                ligne.idPassage());
    }

    /// Résultat **espèce** rattaché à un passage : libellé = nom (vernaculaire, sinon latin, sinon code)
    /// + code entre parenthèses ; détail = le taxon parent (ex. « Chiroptères », si connu) puis la nuit où
    /// elle a été observée. Cliquer ouvre ce passage (mêmes clés d'identité qu'un résultat passage).
    private static ResultatRecherche resultatEspece(EspeceObservee espece) {
        String nom = premierNonVide(espece.nomVernaculaireFr(), espece.nomLatin(), espece.code());
        String libelle = nom + " (" + espece.code() + ")";
        String prefixeGroupe =
                espece.groupe() != null && !espece.groupe().isBlank() ? espece.groupe() + SEPARATEUR : "";
        String details = prefixeGroupe + carreEtPoint(espece.numeroCarre(), espece.codePoint()) + SEPARATEUR + "n°"
                + espece.numeroPassage()
                + (espece.dateEnregistrement() != null ? SEPARATEUR + espece.dateEnregistrement() : "");
        return new ResultatRecherche(
                TypeResultat.ESPECE,
                libelle,
                details,
                espece.numeroCarre(),
                espece.codePoint(),
                null,
                espece.idPassage());
    }

    /// Premier des `candidats` non nul et non blanc (le dernier sert de repli garanti).
    private static String premierNonVide(String... candidats) {
        for (String candidat : candidats) {
            if (candidat != null && !candidat.isBlank()) {
                return candidat;
            }
        }
        return "";
    }

    /// Vrai si au moins un des `champs` (non nuls) contient l'`aiguille` déjà normalisée.
    private static boolean correspond(String aiguille, String... champs) {
        for (String champ : champs) {
            if (champ != null && NormalisationTexte.normaliser(champ).contains(aiguille)) {
                return true;
            }
        }
        return false;
    }
}
