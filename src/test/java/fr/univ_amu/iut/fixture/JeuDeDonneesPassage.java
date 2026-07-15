package fr.univ_amu.iut.fixture;

import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.util.Objects;

/// **La topologie d'une nuit, semée une bonne fois** (#1258) : utilisateur → site → point → enregistreur →
/// passage → session → enregistrement original, puis autant de séquences et d'observations qu'on veut.
///
/// ## Pourquoi cette classe existe
///
/// **Soixante-quinze** fichiers de test resèment la même topologie à la main - l'audit de dette n'en
/// annonçait qu'une dizaine. Chaque migration de schéma coûte donc autant de retouches que de copies, et
/// une clé étrangère ajoutée casse des tests qui n'ont **rien** à voir avec elle.
///
/// Le coût est déjà payé plusieurs fois : `observation` référence `sequence` qui référence
/// `recording_session` qui référence `passage`… Écrire un test sur une **observation** oblige à connaître
/// six tables. Ce n'est pas de la rigueur, c'est du bruit : le test parle de la plomberie au lieu de parler
/// de ce qu'il vérifie.
///
/// ## Comment s'en servir
///
/// ```java
/// JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source).semer();
/// long idPipkuh = jeu.ajouterObservation("Pipkuh");
/// long idValidee = jeu.ajouterObservationValidee("Nyclei");
/// ```
///
/// Les valeurs par défaut (utilisateur `u-1`, carré `640380`, point `A1`, enregistreur `SN-1`) sont celles
/// que les tests existants utilisaient déjà. Tout se surcharge avant `semer()` :
///
/// ```java
/// JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
///         .carre("130711")
///         .point("Z41")
///         .statut(StatutWorkflow.DEPOSE)
///         .verdict(Verdict.OK)
///         .semer();
/// ```
///
/// ## Plusieurs passages, qui **partagent** ce qu'ils ont en commun
///
/// Chaque `semer()` sème **un** passage. Pour une topologie à plusieurs nuits, on rappelle la fabrique :
/// l'utilisateur, l'enregistreur, le site (par n° de carré) et le point (par code) sont **trouvés s'ils
/// existent déjà, créés sinon**. Deux nuits sur le même point partagent donc réellement ce point, ce que
/// les tests d'agrégation (statut dominant d'un point) exigent :
///
/// ```java
/// JeuDeDonneesPassage.dans(source).point("A1").nuit(1, 2025, "2025-06-20").statut(TRANSFORME).verdict(OK).semer();
/// JeuDeDonneesPassage.dans(source).point("A1").nuit(1, 2026, "2026-06-20").statut(VERIFIE).verdict(DOUTEUX).semer();
/// ```
///
/// (Deux passages sur un même point doivent différer par (année, n°) - la règle d'unicité R5.)
///
/// ## Ce qu'elle ne fait pas
///
/// Elle ne **migre pas** le schéma : c'est au test de le faire (via `MigrationSchema` ou son injecteur),
/// parce que tous ne l'obtiennent pas de la même façon.
///
/// Elle ne sème **aucun taxon** : le référentiel réel est déjà posé par la migration `V02__seed_taxons.sql`.
/// `Pipkuh`, `Pippip`, `Nyclei` **existent** - les réinsérer viole la clé primaire (piège coûté une fois).
///
/// Et les **outils de capture** de `src/main/.../outils` restent **autonomes** : ce sont des exécutables
/// indépendants, cette fixture de test ne leur est pas accessible, et on assume leur duplication.
public final class JeuDeDonneesPassage {

    private final SourceDeDonnees source;

    private String idUtilisateur = "u-1";
    private String nomUtilisateur = "Testeur";
    private String numeroCarre = "640380";
    private String nomSite = "Site de test";
    private String codePoint = "A1";
    private Double latitude;
    private Double longitude;
    private String numeroSerie = "SN-1";
    private int numeroPassage = 1;
    private int annee = 2026;
    private String dateNuit = "2026-07-03";
    private StatutWorkflow statut = StatutWorkflow.IMPORTE;
    private Verdict verdict;

    private Long idSite;
    private Long idPoint;
    private Long idPassage;
    private Long idSession;
    private Long idOriginal;
    private Long idResultats;
    private int rangSequence;

    private JeuDeDonneesPassage(SourceDeDonnees source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public static JeuDeDonneesPassage dans(SourceDeDonnees source) {
        return new JeuDeDonneesPassage(source);
    }

    // ─── Surcharges, avant semer() ──────────────────────────────────────────────────────────────────

    public JeuDeDonneesPassage utilisateur(String id) {
        this.idUtilisateur = id;
        return this;
    }

    public JeuDeDonneesPassage carre(String numero) {
        this.numeroCarre = numero;
        return this;
    }

    /// Nom convivial du site (celui qu'affiche la carte). Posé à la **création** du site seulement : deux
    /// nuits sur le même carré partagent le site déjà créé, nom compris.
    public JeuDeDonneesPassage nomSite(String nom) {
        this.nomSite = nom;
        return this;
    }

    public JeuDeDonneesPassage point(String code) {
        this.codePoint = code;
        return this;
    }

    /// Position du point : nécessaire dès qu'un test touche à la carte ou à l'audit des points.
    public JeuDeDonneesPassage position(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        return this;
    }

    public JeuDeDonneesPassage enregistreur(String numeroSerie) {
        this.numeroSerie = numeroSerie;
        return this;
    }

    public JeuDeDonneesPassage nuit(int numero, int annee, String date) {
        this.numeroPassage = numero;
        this.annee = annee;
        this.dateNuit = date;
        return this;
    }

    public JeuDeDonneesPassage statut(StatutWorkflow statut) {
        this.statut = statut;
        return this;
    }

    /// Verdict de vérification du passage (#1258 : les tests de la vue multi-sites en dépendent). `null`
    /// par défaut - une nuit non vérifiée n'a pas de verdict.
    public JeuDeDonneesPassage verdict(Verdict verdict) {
        this.verdict = verdict;
        return this;
    }

    // ─── Semis ──────────────────────────────────────────────────────────────────────────────────────

    /// Sème toute la chaîne, jusqu'à l'enregistrement original. À appeler une fois par passage, avant tout
    /// `ajouter*`. L'utilisateur, l'enregistreur, le site (par n° de carré) et le point (par code) sont
    /// **trouvés s'ils existent déjà, créés sinon** : deux `semer()` qui partagent ces coordonnées sèment
    /// deux passages sur le **même** point, sans doublonner ni violer de clé.
    public JeuDeDonneesPassage semer() {
        trouverOuCreerUtilisateur();
        trouverOuCreerEnregistreur();
        idSite = trouverOuCreerSite();
        idPoint = trouverOuCreerPoint();
        idPassage = new PassageDao(source)
                .insert(new Passage(
                        null,
                        numeroPassage,
                        annee,
                        dateNuit,
                        "22:00",
                        "06:00",
                        null,
                        statut,
                        verdict,
                        null,
                        null,
                        null,
                        idPoint,
                        numeroSerie))
                .id();
        idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(null, "/ws/session", null, null, idPassage))
                .id();
        idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(null, "brut.wav", "/ws/brut.wav", 5.0, 384000, null, idSession))
                .id();
        return this;
    }

    /// Un **jeu de résultats Tadarida** rattaché au passage (la ligne `identification_results` dont les
    /// observations d'un import CSV portent la référence). Optionnel : une observation peut vivre sans -
    /// c'est le cas de celles rapatriées de la plateforme.
    ///
    /// Une fois posé, il est porté par **toutes** les observations ajoutées ensuite.
    public long ajouterResultats() {
        exigerSemis();
        idResultats = new ResultatsIdentificationDao(source)
                .insert(new ResultatsIdentification(null, "/ws/transformes/obs.csv", "Vu", "2026-06-21", idPassage))
                .id();
        return idResultats;
    }

    /// Une séquence d'écoute de plus sur la session semée (rang auto-incrémenté).
    public long ajouterSequence() {
        exigerSemis();
        int rang = rangSequence++;
        return new SequenceDao(source)
                .insert(new SequenceDEcoute(
                        null,
                        "seq" + rang + ".wav",
                        idOriginal,
                        rang,
                        0.0,
                        5.0,
                        "/ws/seq" + rang + ".wav",
                        false,
                        idSession))
                .id();
    }

    /// Une détection **non revue** : Tadarida propose, personne n'a encore rien dit.
    public long ajouterObservation(String taxonTadarida) {
        return ajouterObservation(taxonTadarida, null, ModeValidation.NON_VALIDE, false, null);
    }

    /// Une détection **validée** : l'observateur retient la proposition de Tadarida.
    public long ajouterObservationValidee(String taxonTadarida) {
        return ajouterObservation(taxonTadarida, taxonTadarida, ModeValidation.MANUEL, false, Certitude.PROBABLE);
    }

    /// Une détection **corrigée** : l'observateur retient un autre taxon que Tadarida.
    public long ajouterObservationCorrigee(String taxonTadarida, String taxonObservateur) {
        return ajouterObservation(taxonTadarida, taxonObservateur, ModeValidation.MANUEL, false, Certitude.PROBABLE);
    }

    /// Le cas général, quand aucun des raccourcis ci-dessus ne dit exactement ce qu'on veut. Chaque
    /// observation reçoit **sa propre séquence** : c'est la topologie réelle (une détection appartient à un
    /// extrait sonore).
    public long ajouterObservation(
            String taxonTadarida, String taxonObservateur, ModeValidation mode, boolean douteux, Certitude certitude) {
        long idSequence = ajouterSequence();
        return new ObservationDao(source)
                .insert(new Observation(
                        null,
                        idSequence,
                        0.1,
                        0.4,
                        45,
                        taxonTadarida,
                        0.9,
                        null,
                        taxonObservateur,
                        taxonObservateur != null ? 1.0 : null,
                        null,
                        false,
                        mode,
                        idResultats,
                        douteux,
                        null,
                        null,
                        certitude,
                        null,
                        null))
                .id();
    }

    // ─── Ce que le test a semé ──────────────────────────────────────────────────────────────────────

    public String idUtilisateur() {
        return idUtilisateur;
    }

    public long idSite() {
        exigerSemis();
        return idSite;
    }

    public long idPoint() {
        exigerSemis();
        return idPoint;
    }

    public long idPassage() {
        exigerSemis();
        return idPassage;
    }

    public long idSession() {
        exigerSemis();
        return idSession;
    }

    public long idOriginal() {
        exigerSemis();
        return idOriginal;
    }

    private void exigerSemis() {
        if (idPassage == null) {
            throw new IllegalStateException("Appelez semer() avant d'utiliser le jeu de données.");
        }
    }

    // ─── Trouver-ou-créer : ce qui est partagé entre passages n'est semé qu'une fois ────────────────────

    private void trouverOuCreerUtilisateur() {
        UtilisateurDao dao = new UtilisateurDao(source);
        if (dao.findById(idUtilisateur).isEmpty()) {
            dao.insert(new Utilisateur(idUtilisateur, nomUtilisateur));
        }
    }

    private void trouverOuCreerEnregistreur() {
        EnregistreurDao dao = new EnregistreurDao(source);
        if (dao.findById(numeroSerie).isEmpty()) {
            dao.insert(new Enregistreur(numeroSerie, null, null));
        }
    }

    private long trouverOuCreerSite() {
        SiteDao dao = new SiteDao(source);
        return dao.findByUtilisateur(idUtilisateur).stream()
                .filter(site -> site.numeroCarre().equals(numeroCarre))
                .map(Site::id)
                .findFirst()
                .orElseGet(() -> dao.insert(new Site(
                                null, numeroCarre, nomSite, Protocole.STANDARD, null, "2026-01-01", idUtilisateur))
                        .id());
    }

    private long trouverOuCreerPoint() {
        PointDao dao = new PointDao(source);
        return dao.findBySite(idSite).stream()
                .filter(point -> point.code().equals(codePoint))
                .map(PointDEcoute::id)
                .findFirst()
                .orElseGet(() -> dao.insert(new PointDEcoute(null, codePoint, latitude, longitude, null, idSite))
                        .id());
    }
}
