package fr.univ_amu.iut.commun.persistence;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.commun.model.EspaceDisque;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.JournalMutations;
import fr.univ_amu.iut.commun.model.TailleFichier;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// **Sauvegarde et restauration** de la base SQLite (#148) : la base concentre tout le travail
/// (sites, passages, observations), sans filet natif. Ce service permet d'en écrire une copie cohérente
/// et de repartir d'une sauvegarde.
///
/// - **Sauvegarde** : `VACUUM INTO` produit un **instantané cohérent** de la base dans un fichier
///   horodaté, même si une connexion est ouverte (contrairement à une copie brute qui pourrait rater le
///   journal WAL). Le fichier obtenu est une base SQLite autonome et compacte.
/// - **Restauration** : on vérifie d'abord que le fichier est une base **lisible**, on met de côté la base
///   courante (**filet de sécurité** avant écrasement, critère #148), on la remplace, on purge les fichiers
///   annexes (`-wal`/`-shm`/`-journal`) puis on **rejoue la migration** (idempotente) pour garantir un schéma
///   à jour : un état cohérent quelle que soit l'ancienneté de la sauvegarde.
///
/// Les connexions du socle sont **de courte durée** (ouvertes/fermées par opération, cf. [SourceDeDonnees]) :
/// aucune connexion longue à fermer pour remplacer le fichier. La restauration reste une action délibérée,
/// à faire hors opération concurrente.
public class ServiceSauvegarde {

    /// Ce qu'une sauvegarde de la **base** emporte, dit à l'utilisateur (#3212).
    ///
    /// L'[ADR 2736](https://companion-dev.echonuit.fr/decisions/2736-le-clair-est-assume-et-annonce/) a
    /// tranché de **ne pas chiffrer** : une clé dérivée d'un mot de passe ferait de la sauvegarde un
    /// piège au moment précis où elle sert. Cette décision n'est tenable que si l'application **dit ce
    /// qu'elle écrit** - sinon l'utilisateur range en aveugle un fichier qui porte les localisations
    /// d'espèces protégées, éventuellement dans un dossier synchronisé sur un service en ligne.
    ///
    /// La phrase vit **ici**, et non dans chaque surface : l'IHM et la CLI doivent dire la **même
    /// chose**, et deux copies d'un même avertissement divergent (ADR 0014). Un constat, jamais une
    /// alarme : sauvegarder est une bonne pratique, le message ne doit pas en dissuader.
    public static final String CE_QU_ELLE_EMPORTE =
            "Elle contient vos localisations de points d'écoute, en clair : rangez-la en conséquence.";

    /// Ce qu'une sauvegarde **complète** emporte en plus : l'audio (#3212). Même règle de partage.
    public static final String CE_QUE_LA_COMPLETE_EMPORTE =
            "Elle contient vos localisations de points d'écoute et vos enregistrements, en clair :"
                    + " rangez-la en conséquence.";

    private static final String PREFIXE = "vigiechiro-sauvegarde-";
    private static final String PREFIXE_COMPLET = "vigiechiro-sauvegarde-complete-";

    /// Caractères hexadécimaux du condensé qui rend unique le nom d'un dossier de session sauvegardé.
    /// Huit suffisent largement : le condensé ne départage que les racines d'une même base, elles se
    /// comptent en dizaines, et il reste lisible à l'œil dans un nom de dossier.
    private static final int LONGUEUR_CONDENSE = 8;
    /// Ce que porte une sauvegarde **en cours de constitution**. En tête du nom, et non en
    /// suffixe : `InventaireSauvegardes` classe sur le **préfixe**, donc un suffixe la laisserait
    /// passer pour complète (#3572).
    static final String PREFIXE_EN_CHANTIER = "en-chantier-";

    private static final String SOUS_DOSSIER_BASE = "base";
    private static final String SOUS_DOSSIER_SESSIONS = "sessions";
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final SourceDeDonnees source;
    private final Horloge horloge;
    private final JournalMutations journal;
    private final InstantaneBase instantane;
    private final EspaceDisque espaceDisque;
    private final TailleFichier tailleFichier;
    private final GestesFichiers gestes;

    @Inject
    public ServiceSauvegarde(SourceDeDonnees source, Horloge horloge, JournalMutations journal) {
        this(source, horloge, EspaceDisque.reel(), journal);
    }

    /// Variante à **espace disque injecté** : la restauration complète choisit son régime d'après la
    /// place libre (#3563), et un test doit pouvoir éprouver les trois sans dépendre de la machine.
    /// Même couture que `CompacteurDepot` et `OutilsImport`, qui gardent aussi une fabrique par défaut
    /// plutôt qu'un binding.
    ServiceSauvegarde(SourceDeDonnees source, Horloge horloge, EspaceDisque espaceDisque, JournalMutations journal) {
        this(source, horloge, espaceDisque, TailleFichier.reelle(), GestesFichiers.reels(), journal);
    }

    /// Variante à **pesée injectée** en plus (#3627). Les deux ports vont ensemble : le garde d'espace
    /// confronte ce que la sauvegarde pèse à ce que le support offre, et il faut pouvoir éprouver les
    /// deux côtés de cette comparaison. Un fichier illisible ne se fabrique pas de façon portable, d'où
    /// le port plutôt qu'un test conditionnel.
    ServiceSauvegarde(
            SourceDeDonnees source,
            Horloge horloge,
            EspaceDisque espaceDisque,
            TailleFichier tailleFichier,
            GestesFichiers gestes,
            JournalMutations journal) {
        this.source = Objects.requireNonNull(source, "source");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
        this.espaceDisque = Objects.requireNonNull(espaceDisque, "espaceDisque");
        this.tailleFichier = Objects.requireNonNull(tailleFichier, "tailleFichier");
        this.gestes = Objects.requireNonNull(gestes, "gestes");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.instantane = new InstantaneBase(this.source);
    }

    /// Écrit une sauvegarde cohérente de la base dans `dossierDestination` (créé au besoin), nommée
    /// `vigiechiro-sauvegarde-AAAAMMJJ-HHMMSS.db`. Renvoie le fichier créé. `dossierDestination` **choisi
    /// par l'appelant** rend l'emplacement configurable (critère #148).
    public Path sauvegarder(Path dossierDestination) {
        Objects.requireNonNull(dossierDestination, "dossierDestination");
        return instantane.ecrireDans(dossierDestination, PREFIXE + HORODATAGE.format(horloge.maintenant()));
    }

    /// Restaure la base depuis `sauvegarde` : vérification, refus d'une sauvegarde trop récente,
    /// filet de sécurité, remplacement, migration, et **retour arrière** si la migration échoue
    /// (#2730). Le détail vit dans [RestaurationBase].
    ///
    /// @throws IllegalArgumentException si `sauvegarde` n'existe pas
    /// @throws DataAccessException si le fichier n'est pas une base lisible, s'il vient d'une version
    ///     plus récente de l'application, ou si la migration de la base restaurée échoue
    public void restaurer(Path sauvegarde) {
        Objects.requireNonNull(sauvegarde, "sauvegarde");
        new RestaurationBase(source).executer(sauvegarde);
        // La base entiere est remplacee : c'est la plus structurelle des mutations. L'annonce est ici
        // et non dans la vue, sans quoi la CLI `restaurer` serait muette (constat de la passe 2 du lot
        // #3537). `restaurerComplet` passe par cette methode : une seule annonce couvre les deux.
        journal.mutationStructurelleValidee();
    }

    /// Dossier de sauvegarde **par défaut** (`<workspace>/sauvegardes`) : proposé quand l'utilisateur ne
    /// choisit pas d'emplacement. L'emplacement reste configurable (paramètre de [#sauvegarder]).
    public Path dossierParDefaut() {
        return source.workspace().racine().resolve("sauvegardes");
    }

    /// **Sauvegarde complète** : base **et** dossiers de session (audio brut/transformé), prérequis d'un
    /// reset sûr (#1142). Contrairement à [#sauvegarder] (base seule, routine), produit un **dossier**
    /// `vigiechiro-sauvegarde-complete-AAAAMMJJ-HHMMSS/` contenant `base/vigiechiro.db` (instantané cohérent
    /// via `VACUUM INTO`) et `sessions/<dossier>/` (copie de chaque `recording_session.root_path` présent).
    /// Action **délibérée** (l'audio peut peser plusieurs Go) : à lancer avant un reset, hors opération
    /// concurrente.
    ///
    /// @return le **bilan** de la sauvegarde : le dossier créé, les sessions copiées, et **celles qui ne
    ///     l'ont pas été** (#1346)
    public BilanSauvegarde sauvegarderComplet(Path dossierDestination) {
        Objects.requireNonNull(dossierDestination, "dossierDestination");
        try {
            refuserSiLaPlaceManque(dossierDestination);
            Path racineBackup = dossierLibreComplet(dossierDestination);
            instantane.ecrire(racineBackup.resolve(SOUS_DOSSIER_BASE).resolve(Workspace.FICHIER_BASE));
            Path dossierSessions = Files.createDirectories(racineBackup.resolve(SOUS_DOSSIER_SESSIONS));
            List<RacineSauvegardee> emportees = new ArrayList<>();
            List<String> inaccessibles = new ArrayList<>();
            for (Path racineSession : racinesSessions()) {
                // Une racine absente n'est PAS une erreur (carte SD non montée, disque débranché) : la
                // sauvegarde doit aboutir. Mais la sauter en silence laissait croire à une copie complète
                // (#1346) : c'est la seule chose qu'on ne peut pas se permettre avant un reset (#1151).
                if (Files.isDirectory(racineSession)) {
                    emportees.add(emporter(racineSession, dossierSessions));
                } else {
                    inaccessibles.add(racineSession.toString());
                }
            }
            ManifesteSauvegardeJson.ecrire(racineBackup, ManifesteSauvegarde.courant(emportees));
            // Le nom n'arrive qu'ICI, et c'est toute la décision : une sauvegarde ne porte le sien
            // qu'une fois complète (#3572).
            Path nommee = nommer(racineBackup);
            return new BilanSauvegarde(nommee, emportees.size(), inaccessibles);
        } catch (IOException | SQLException echec) {
            throw new DataAccessException("Sauvegarde complète impossible vers " + dossierDestination, echec);
        }
    }

    /// Restaure une **sauvegarde complète** produite par [#sauvegarderComplet] : la base **et** les
    /// dossiers de son, remis là où ils étaient, avec une base corrigée pour les y retrouver (#2727).
    ///
    /// L'ordre est celui d'une opération **vérifiée puis basculée** : on confronte d'abord chaque
    /// dossier de la sauvegarde à l'inventaire du manifeste, et une seule discordance annule tout
    /// avant que rien n'ait été touché. Ce n'est qu'ensuite que la base est remplacée (via
    /// [#restaurer] : vérification, filet de sécurité, migration), que les dossiers sont replacés,
    /// puis que les `root_path` sont réécrits en une transaction.
    ///
    /// Une sauvegarde **antérieure au manifeste** ne porte pas cette information : ses dossiers
    /// reviennent à la racine du workspace et la base n'est pas corrigée, exactement comme avant. Le
    /// bilan le dit, plutôt que de laisser croire à mieux.
    ///
    /// Action délibérée, hors opération concurrente.
    ///
    /// @return ce qui a été replacé, ce qui a changé de place, et ce que la sauvegarde ne contenait pas
    /// @throws IllegalArgumentException si le dossier ou sa base sont introuvables
    /// @throws DataAccessException si un dossier de la sauvegarde ne correspond pas à son inventaire
    public BilanRestauration restaurerComplet(Path dossierBackup) {
        Objects.requireNonNull(dossierBackup, "dossierBackup");
        RestaurationComplete restauration = new RestaurationComplete(source, espaceDisque);
        Optional<ManifesteSauvegarde> manifeste = ManifesteSauvegardeJson.lire(dossierBackup);
        manifeste.ifPresent(present -> restauration.verifierLaSauvegarde(dossierBackup, present));
        restaurer(dossierBackup.resolve(SOUS_DOSSIER_BASE).resolve(Workspace.FICHIER_BASE));
        return manifeste
                .map(present -> restauration.replacer(dossierBackup, present))
                .orElseGet(() -> restauration.replacerSansManifeste(dossierBackup));
    }

    /// Copie une racine de session sous `sessions/<identifiant>` et rend son entrée de manifeste.
    ///
    /// L'inventaire est dressé **sur la copie**, pas sur l'original : ce qu'on veut décrire, c'est ce
    /// que la sauvegarde contient réellement.
    private static RacineSauvegardee emporter(Path racineSession, Path dossierSessions) throws IOException {
        String identifiant = identifiantDe(racineSession);
        Path destination = dossierSessions.resolve(identifiant);
        ArborescenceFichiers.copier(racineSession, destination);
        return RacineSauvegardee.de(identifiant, racineSession.toString(), InventaireDossier.de(destination));
    }

    /// Nom de dossier **lisible et unique** pour une racine de session : son dernier segment, suivi
    /// d'un court condensé de son chemin complet.
    ///
    /// Le seul dernier segment ne suffit pas, et c'est tout le défaut corrigé ici (#2726) :
    /// `/mnt/disque-a/Nuit-01` et `/mnt/disque-b/Nuit-01` visaient la même destination, et la copie
    /// récursive écrasant en `REPLACE_EXISTING`, la seconde racine fusionnait dans la première sans
    /// un mot. Le condensé est ce qui rend la collision impossible ; le segment lisible est ce qui
    /// permet encore de s'y retrouver en ouvrant le dossier.
    private static String identifiantDe(Path racineSession) {
        Path dernierSegment = racineSession.getFileName();
        String lisible = dernierSegment == null ? "racine" : dernierSegment.toString();
        String condense = Empreintes.sha256Hex(racineSession.toString().getBytes(StandardCharsets.UTF_8));
        return lisible + "-" + condense.substring(0, LONGUEUR_CONDENSE);
    }

    /// Racines des sessions d'enregistrement (`recording_session.root_path`), lues directement : ce service
    /// socle sauvegarde la base dans son ensemble, connaître ses tables lui revient.
    private List<Path> racinesSessions() throws SQLException {
        List<Path> racines = new ArrayList<>();
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT DISTINCT root_path FROM recording_session WHERE root_path IS NOT NULL")) {
            while (rs.next()) {
                racines.add(Path.of(rs.getString(1)));
            }
        }
        return racines;
    }

    /// Refuse **avant la première copie** quand la place manque, en chiffrant ce qui manque.
    ///
    /// L'import, le lot et la restauration gardent tous la place avant d'écrire ; la sauvegarde, qui
    /// écrit le plus et vers un support **choisi par l'utilisateur** - souvent une clé, souvent
    /// petite - ne le faisait pas (#3572). Elle copiait jusqu'à saturation, échouait à mi-parcours, et
    /// laissait un dossier qui ressemblait à une sauvegarde.
    ///
    /// ⚠️ Le besoin se mesure **sur le disque** ici, contrairement à la restauration où le manifeste le
    /// portait déjà : les racines existent, il n'y a pas encore de manifeste à lire. Une racine
    /// inaccessible ne compte pas - elle ne sera pas copiée, et la sauvegarde l'annonce (#1346).
    private void refuserSiLaPlaceManque(Path dossierDestination) throws IOException, SQLException {
        Files.createDirectories(dossierDestination);
        long requis = Files.size(source.workspace().cheminBaseDeDonnees());
        List<ArborescenceFichiers.EchecLecture> illisibles = new ArrayList<>();
        for (Path racineSession : racinesSessions()) {
            if (Files.isDirectory(racineSession)) {
                ArborescenceFichiers.Pesee pesee = ArborescenceFichiers.peser(racineSession, tailleFichier, gestes);
                requis += pesee.octets();
                illisibles.addAll(pesee.illisibles());
            }
        }
        refuserSiLaMesureEstPartielle(illisibles);
        long libre = espaceDisque.disponibleOctets(dossierDestination);
        if (libre < requis) {
            throw new RefusAvantEcriture(
                    "Il n'y a pas assez de place dans " + dossierDestination + " : la sauvegarde pèse "
                            + Formats.octetsLisibles(requis) + " et il reste " + Formats.octetsLisibles(libre)
                            + ". Libérez " + Formats.octetsLisibles(Math.max(1024, requis - libre))
                            + ", ou sauvegardez vers un autre emplacement. Rien n'a été touché.",
                    null);
        }
    }

    /// Refuse quand la pesée n'a **pas tout vu**, plutôt que de comparer un minorant à la place libre.
    ///
    /// Un fichier illisible pèse zéro dans le total. Additionnés, ces zéros font une sauvegarde qui
    /// paraît tenir, part, et échoue à mi-parcours : exactement la panne que ce garde existe pour
    /// empêcher (#3627). C'est la forme que l'ADR 2213 décrit - un dispositif ne conclut pas avant
    /// d'avoir rapporté ce qu'il a vu.
    ///
    /// Le refus **nomme** le premier fichier et sa raison, sur le modèle de `BesoinDePlace` : « il y a
    /// un problème » n'aide personne à le lever.
    private void refuserSiLaMesureEstPartielle(List<ArborescenceFichiers.EchecLecture> illisibles) {
        if (illisibles.isEmpty()) {
            return;
        }
        ArborescenceFichiers.EchecLecture premier = illisibles.getFirst();
        throw new RefusAvantEcriture(
                "Impossible de mesurer ce que la sauvegarde va peser : " + illisibles.size()
                        + " fichier(s) sont illisibles, dont " + premier.chemin() + " ("
                        + premier.cause().getMessage() + "). La sauvegarde est annulée plutôt que tentée à"
                        + " l'aveugle : elle échouerait en cours de copie en laissant un dossier qui"
                        + " ressemble à une sauvegarde. Rien n'a été touché.",
                premier.cause());
    }

    /// Donne enfin son nom à la sauvegarde, une fois le manifeste écrit.
    ///
    /// Le renommage ferme ce qu'un nettoyage à l'échec ne fermerait pas : une coupure de courant ou un
    /// `kill -9` ne laissent tourner aucun code. Tant que le dossier porte son nom de chantier,
    /// `InventaireSauvegardes` ne le reconnaît pas, et il ne peut donc pas se faire passer pour une
    /// sauvegarde complète.
    private Path nommer(Path enChantier) throws IOException {
        Path nommee =
                enChantier.resolveSibling(enChantier.getFileName().toString().substring(PREFIXE_EN_CHANTIER.length()));
        return Files.move(enChantier, nommee);
    }

    /// Premier dossier de sauvegarde complète libre (horodaté, suffixé `-1`, `-2`… en cas de collision).
    ///
    /// Il est créé sous un nom **de chantier**, que rien ne reconnaît comme une sauvegarde : le nom
    /// définitif n'arrive qu'avec [#nommer]. La collision se cherche sur le nom **définitif**, sans quoi
    /// deux sauvegardes de la même seconde se marcheraient dessus au renommage.
    private Path dossierLibreComplet(Path dossier) throws IOException {
        Files.createDirectories(dossier);
        String base = PREFIXE_COMPLET + HORODATAGE.format(horloge.maintenant());
        String nom = base;
        int suffixe = 1;
        while (Files.exists(dossier.resolve(nom)) || Files.exists(dossier.resolve(PREFIXE_EN_CHANTIER + nom))) {
            nom = base + "-" + suffixe++;
        }
        return Files.createDirectories(dossier.resolve(PREFIXE_EN_CHANTIER + nom));
    }
}
