package fr.univ_amu.iut.multisite.model;

import fr.univ_amu.iut.commun.model.EcrivainCsv;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.multisite.model.dao.SavedViewDao;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/// Service métier de la feature `multisite` : construit la **vue agrégée multi-sites** du
/// parcours P5 (épopée E5), un tableau haute densité listant tous les passages de tous les sites
/// d'un utilisateur avec leurs informations clés. Suit le patron du service de référence
/// `ServiceSites` : pure Java testable, dépendances reçues par constructeur, aucun import JavaFX.
///
/// Trois responsabilités :
///
/// - **Agrégation** ([#listerPassages(String)]) : croise les DAO de `sites` (sites + points) et
///   de `passage` pour aplatir chaque passage en [LignePassage] (P5-CA2). Lecture seule.
/// - **Tri et filtres** ([FiltresMultisite], [TriMultisite]) : filtrage par site, statut, verdict,
///   année ; tri stable et déterministe sur ces mêmes axes.
/// - **Vues sauvegardées** (CRUD sur [SavedViewDao]) : enregistre/charge un jeu de filtres
///   sérialisé en JSON (`saved_view.filters_json`, story E5.S3).
///
/// Dépendances inter-features (lecture seule des DAO, jamais des vues) : `multisite → sites` et
/// `multisite → passage`. Aucune feature ne dépendant de `multisite`, le graphe reste acyclique
/// (contrôlé par `ArchitectureTest`).
///
/// L'[Horloge] est reçue par constructeur (patron du service de référence) et sert à la vue
/// « saison courante » ([#listerPassagesDeLaSaison(String)]), pour rester déterministe en test
/// plutôt que d'appeler `LocalDate.now()`.
public class ServiceMultisite {

    /// En-tête du tableau / export CSV : ordre stable des colonnes (P5-CA2).
    private static final List<String> ENTETE =
            List.of("site", "point", "annee", "passage", "date", "statut", "verdict");

    /// Nom du paramètre `filtres` (messages `requireNonNull`).
    private static final String FILTRES = "filtres";

    private final SavedViewDao savedViewDao;
    private final SiteDao siteDao;
    private final PointDao pointDao;
    private final PassageDao passageDao;
    private final Horloge horloge;

    public ServiceMultisite(
            SavedViewDao savedViewDao, SiteDao siteDao, PointDao pointDao, PassageDao passageDao, Horloge horloge) {
        this.savedViewDao = Objects.requireNonNull(savedViewDao, "savedViewDao");
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    // --- Agrégation, tri, filtres ---

    /// Tous les passages de tous les sites de l'utilisateur, tri de lecture par défaut (par site).
    public List<LignePassage> listerPassages(String idUtilisateur) {
        return listerPassages(idUtilisateur, FiltresMultisite.aucun(), TriMultisite.PAR_SITE);
    }

    /// Passages filtrés, tri de lecture par défaut (par site).
    public List<LignePassage> listerPassages(String idUtilisateur, FiltresMultisite filtres) {
        return listerPassages(idUtilisateur, filtres, TriMultisite.PAR_SITE);
    }

    /// Vue agrégée des passages de l'utilisateur, filtrée puis triée.
    ///
    /// Parcourt les sites de l'utilisateur ([SiteDao#findByUtilisateur(String)]), leurs
    /// points ([PointDao#findBySite(Long)]) et les passages de chaque point
    /// ([PassageDao#findByPoint(Long)]), aplatit chaque passage en [LignePassage], ne conserve que
    /// les lignes acceptées par `filtres`, et trie selon `tri`.
    ///
    /// @return liste mutable et triée (sûre à réordonner par l'appelant)
    public List<LignePassage> listerPassages(String idUtilisateur, FiltresMultisite filtres, TriMultisite tri) {
        Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        Objects.requireNonNull(filtres, FILTRES);
        Objects.requireNonNull(tri, "tri");
        List<LignePassage> lignes = new ArrayList<>();
        for (Site site : siteDao.findByUtilisateur(idUtilisateur)) {
            for (PointDEcoute point : pointDao.findBySite(site.id())) {
                for (Passage passage : passageDao.findByPoint(point.id())) {
                    LignePassage ligne = new LignePassage(
                            passage.id(),
                            site.numeroCarre(),
                            point.code(),
                            passage.annee(),
                            passage.numeroPassage(),
                            passage.dateEnregistrement(),
                            passage.statutWorkflow(),
                            passage.verdictVerification());
                    if (filtres.accepte(ligne)) {
                        lignes.add(ligne);
                    }
                }
            }
        }
        lignes.sort(tri.comparateur());
        return lignes;
    }

    /// Vue « saison courante » : passages de l'année lue de l'[Horloge] (P5-CA1 « nb passages de
    /// la saison »). Tri de lecture par défaut.
    public List<LignePassage> listerPassagesDeLaSaison(String idUtilisateur) {
        int saison = horloge.aujourdhui().getYear();
        return listerPassages(idUtilisateur, FiltresMultisite.parAnnee(saison));
    }

    /// Agrège les sites de l'utilisateur **pour la carte** (#152) : par carré, ses points (avec GPS, code,
    /// nombre de passages et statut workflow dominant) et le total de passages. Vue **non filtrée** (la
    /// carte est une vue d'ensemble ; le filtrage carte↔tableau viendra séparément). Données domaine : la
    /// couche `view` les traduit en marqueurs/emprises colorés.
    public List<CarreAgrege> agregerPourCarte(String idUtilisateur) {
        Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        List<CarreAgrege> carres = new ArrayList<>();
        for (Site site : siteDao.findByUtilisateur(idUtilisateur)) {
            List<PointAgrege> points = new ArrayList<>();
            int passagesDuCarre = 0;
            for (PointDEcoute point : pointDao.findBySite(site.id())) {
                List<Passage> passages = passageDao.findByPoint(point.id());
                passagesDuCarre += passages.size();
                points.add(new PointAgrege(
                        point.code(), point.latitude(), point.longitude(), passages.size(), statutDominant(passages)));
            }
            carres.add(new CarreAgrege(site.numeroCarre(), site.nomConvivial(), points, passagesDuCarre));
        }
        return carres;
    }

    /// Statut workflow du passage **le plus récent** (année puis n° de passage), représentatif de l'état
    /// courant du point sur la carte ; `null` si le point n'a aucun passage.
    private static StatutWorkflow statutDominant(List<Passage> passages) {
        return passages.stream()
                .max(Comparator.comparingInt(Passage::annee).thenComparingInt(Passage::numeroPassage))
                .map(Passage::statutWorkflow)
                .orElse(null);
    }

    /// Sérialise des lignes de la vue en CSV déterministe (en-tête + une ligne par passage), via
    /// l'écrivain partagé [EcrivainCsv]. Format identique à toute exécution (support des exports
    /// P5-CA5 et des tests « golden »). Un verdict absent est rendu par une cellule vide.
    public String exporterCsv(List<LignePassage> lignes) {
        return new EcrivainCsv().versChaine(tableCsv(lignes));
    }

    /// Écrit la vue agrégée en CSV déterministe dans `destination` (export P5-CA5 : c'est le service
    /// qui matérialise sur disque, la couche IHM ne fait que choisir le fichier). Crée les dossiers
    /// parents au besoin.
    ///
    /// @throws java.io.UncheckedIOException si l'écriture échoue
    public void exporterCsvVers(Path destination, List<LignePassage> lignes) {
        Objects.requireNonNull(destination, "destination");
        new EcrivainCsv().ecrire(destination, tableCsv(lignes));
    }

    private static List<List<String>> tableCsv(List<LignePassage> lignes) {
        Objects.requireNonNull(lignes, "lignes");
        List<List<String>> table = new ArrayList<>();
        table.add(ENTETE);
        for (LignePassage ligne : lignes) {
            table.add(Arrays.asList(
                    ligne.numeroCarre(),
                    ligne.codePoint(),
                    String.valueOf(ligne.annee()),
                    String.valueOf(ligne.numeroPassage()),
                    ligne.dateEnregistrement(),
                    ligne.statut().libelle(),
                    ligne.verdict() == null ? "" : ligne.verdict().libelle()));
        }
        return table;
    }

    // --- Vues sauvegardées (CRUD) ---

    /// Enregistre un jeu de filtres sous un nom (story E5.S3). Les critères sont sérialisés en JSON
    /// dans `saved_view.filters_json`.
    ///
    /// @return la vue insérée, avec son `id` auto-généré
    public SavedView enregistrerVue(String nom, FiltresMultisite filtres) {
        Objects.requireNonNull(nom, "nom");
        Objects.requireNonNull(filtres, FILTRES);
        return savedViewDao.insert(new SavedView(null, nom, FiltresMultisiteJson.serialiser(filtres)));
    }

    /// Toutes les vues sauvegardées (menu « ⭐ Mes vues »).
    public List<SavedView> listerVues() {
        return savedViewDao.findAll();
    }

    /// Recharge les critères d'une vue sauvegardée (rejoue une combinaison de filtres sans la
    /// ressaisir).
    ///
    /// @throws RegleMetierException si aucune vue ne porte cet identifiant
    public FiltresMultisite chargerVue(Long idVue) {
        SavedView vue = savedViewDao
                .findById(idVue)
                .orElseThrow(() -> new RegleMetierException("Vue sauvegardée introuvable : " + idVue));
        return FiltresMultisiteJson.interpreter(vue.filtresJson());
    }

    /// Applique une vue sauvegardée à la vue agrégée : charge ses filtres puis liste les passages
    /// correspondants (tri de lecture par défaut).
    ///
    /// @throws RegleMetierException si aucune vue ne porte cet identifiant
    public List<LignePassage> appliquerVue(String idUtilisateur, Long idVue) {
        return listerPassages(idUtilisateur, chargerVue(idVue));
    }

    /// Met à jour le nom et les critères d'une vue existante.
    ///
    /// @return la vue mise à jour
    /// @throws RegleMetierException si aucune vue ne porte cet identifiant
    public SavedView mettreAJourVue(Long idVue, String nouveauNom, FiltresMultisite filtres) {
        Objects.requireNonNull(nouveauNom, "nouveauNom");
        Objects.requireNonNull(filtres, FILTRES);
        SavedView existante = savedViewDao
                .findById(idVue)
                .orElseThrow(() -> new RegleMetierException("Vue sauvegardée introuvable : " + idVue));
        SavedView miseAJour = new SavedView(existante.id(), nouveauNom, FiltresMultisiteJson.serialiser(filtres));
        savedViewDao.update(miseAJour);
        return miseAJour;
    }

    /// Supprime une vue sauvegardée (idempotent : sans effet si elle n'existe pas).
    public void supprimerVue(Long idVue) {
        savedViewDao.delete(idVue);
    }
}
