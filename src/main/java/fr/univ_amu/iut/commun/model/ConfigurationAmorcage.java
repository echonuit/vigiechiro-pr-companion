package fr.univ_amu.iut.commun.model;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Configuration lue **avant que quoi que ce soit ne démarre** : elle dit où vivent l'espace de
/// travail et la base SQLite.
///
/// Elle ne peut pas être un réglage applicatif ordinaire, parce que les réglages vivent **dans** la
/// base : on ne peut pas demander à la base où elle se trouve. C'est la seule configuration de
/// l'application qui vive en dehors d'elle.
///
/// ## Où elle vit
///
/// | Système | Dossier |
/// |---|---|
/// | Windows | `%APPDATA%\vigiechiro\` |
/// | Ailleurs | `$XDG_CONFIG_HOME/vigiechiro/`, repli `~/.config/vigiechiro/` |
///
/// Le repli XDG n'est pas une commodité : sous Flatpak, `~/.config` est un chemin **masqué**, et
/// `$XDG_CONFIG_HOME` désigne le dossier privé réellement accessible au bac à sable. Coder
/// `~/.config` en dur donnerait une application qui ne sait pas se configurer une fois empaquetée.
///
/// ## Ce qu'elle ne fait pas
///
/// Elle ne **valide** aucun chemin : un dossier inaccessible se refuse au moment où on le choisit,
/// pas au démarrage, où l'on ne saurait rien en faire d'utile. Un fichier absent, illisible ou
/// abîmé rend une configuration **vide**, jamais une exception : l'application démarre sur ses
/// emplacements par défaut plutôt que de refuser de s'ouvrir.
public record ConfigurationAmorcage(Optional<Path> espaceDeTravail, Optional<Path> cheminBase) {

    /// Propriété système qui redirige le dossier de configuration. Elle existe pour les **tests** :
    /// sans elle, une suite lirait la configuration réelle de qui lance le build, et passerait ou
    /// échouerait selon la machine.
    public static final String PROP_DOSSIER = "vigiechiro.config";

    private static final String DOSSIER_APPLICATION = "vigiechiro";
    private static final String FICHIER = "amorcage.properties";
    private static final String CLE_ESPACE_DE_TRAVAIL = "espace-de-travail";
    private static final String CLE_BASE = "base";
    private static final Logger LOG = Logger.getLogger(ConfigurationAmorcage.class.getName());

    public ConfigurationAmorcage {
        Objects.requireNonNull(espaceDeTravail, "espaceDeTravail");
        Objects.requireNonNull(cheminBase, "cheminBase");
    }

    /// Configuration vide : aucun emplacement choisi, tout reste au défaut.
    public static ConfigurationAmorcage vide() {
        return new ConfigurationAmorcage(Optional.empty(), Optional.empty());
    }

    /// Dossier de configuration effectif, propriété système prioritaire.
    public static Path dossier() {
        String redirection = System.getProperty(PROP_DOSSIER);
        if (redirection != null && !redirection.isBlank()) {
            return Path.of(redirection);
        }
        return dossierPour(
                System.getProperty("os.name", ""),
                System.getenv("APPDATA"),
                System.getenv("XDG_CONFIG_HOME"),
                System.getProperty("user.home"));
    }

    /// Règle de placement, isolée de l'environnement pour être **éprouvable** : les variables
    /// d'environnement ne se posent pas depuis un test Java, et une règle qu'on ne peut pas exercer
    /// sur les trois systèmes n'est vérifiée sur aucun.
    static Path dossierPour(String nomSysteme, String appdata, String xdgConfigHome, String dossierPersonnel) {
        if (nomSysteme.toLowerCase(Locale.ROOT).startsWith("windows")) {
            Path racine = estRenseigne(appdata) ? Path.of(appdata) : Path.of(dossierPersonnel, "AppData", "Roaming");
            return racine.resolve(DOSSIER_APPLICATION);
        }
        Path racine = estRenseigne(xdgConfigHome) ? Path.of(xdgConfigHome) : Path.of(dossierPersonnel, ".config");
        return racine.resolve(DOSSIER_APPLICATION);
    }

    private static boolean estRenseigne(String valeur) {
        return valeur != null && !valeur.isBlank();
    }

    /// Configuration lue dans le dossier effectif.
    public static ConfigurationAmorcage lue() {
        return lueDepuis(dossier());
    }

    /// Configuration lue dans `dossier`. Rend une configuration **vide** si le fichier n'existe pas
    /// ou n'est pas exploitable : l'amorçage ne doit jamais empêcher l'application de s'ouvrir.
    public static ConfigurationAmorcage lueDepuis(Path dossier) {
        Path fichier = dossier.resolve(FICHIER);
        if (!Files.isRegularFile(fichier)) {
            return vide();
        }
        Properties proprietes = new Properties();
        try (InputStream flux = Files.newInputStream(fichier)) {
            proprietes.load(flux);
        } catch (IOException | IllegalArgumentException illisible) {
            LOG.log(
                    Level.WARNING,
                    illisible,
                    () -> "Configuration d'amorçage illisible, emplacements par défaut : " + fichier);
            return vide();
        }
        return new ConfigurationAmorcage(chemin(proprietes, CLE_ESPACE_DE_TRAVAIL), chemin(proprietes, CLE_BASE));
    }

    /// Écrit cette configuration dans `dossier`, en le créant au besoin. Une valeur absente est
    /// **omise** du fichier plutôt qu'écrite vide : relire ce qu'on vient d'écrire redonne la même
    /// configuration.
    ///
    /// L'écriture passe par [EcritureAtomique] : ce fichier est relu **avant que quoi que ce soit ne
    /// démarre**, et [#lueDepuis] pardonne un fichier illisible en rendant une configuration vide.
    /// Écrit en direct, il serait tronqué à l'ouverture du flux, et une coupure laisserait un fichier
    /// incomplet que le prochain lancement remplacerait **en silence** par les emplacements par défaut
    /// (#3507). L'indulgence de la lecture est une bonne décision ; c'est à l'écriture de garantir
    /// qu'elle ne voie jamais un fichier à moitié écrit.
    public void enregistrerDans(Path dossier) throws IOException {
        Properties proprietes = new Properties();
        espaceDeTravail.ifPresent(chemin -> proprietes.setProperty(CLE_ESPACE_DE_TRAVAIL, chemin.toString()));
        cheminBase.ifPresent(chemin -> proprietes.setProperty(CLE_BASE, chemin.toString()));
        Files.createDirectories(dossier);
        EcritureAtomique.ecrire(dossier.resolve(FICHIER), rendu(proprietes));
    }

    /// Le texte exact que `Properties.store` aurait écrit dans un flux d'octets.
    ///
    /// Le détour par un flux d'octets n'est pas une lourdeur : `store(OutputStream)` écrit en
    /// **ISO-8859-1** et échappe tout caractère non ASCII en séquence Unicode à quatre chiffres
    /// hexadécimaux, quand `store(Writer)` écrirait le texte brut. (La séquence ne peut pas être citée
    /// ici : Java l'interprète jusque dans les commentaires, et un exemple ferait échouer la
    /// compilation.)
    /// Or [#lueDepuis] relit avec `load(InputStream)`, donc en ISO-8859-1. Passer par un
    /// `StringWriter` paraîtrait plus simple et casserait la relecture du **premier dossier de travail
    /// accentué** venu. Le rendu obtenu ici est purement ASCII, donc identique en UTF-8.
    private static String rendu(Properties proprietes) throws IOException {
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();
        proprietes.store(tampon, "VigieChiro Companion - emplacements lus au demarrage");
        return tampon.toString(StandardCharsets.ISO_8859_1);
    }

    private static Optional<Path> chemin(Properties proprietes, String cle) {
        String valeur = proprietes.getProperty(cle);
        return valeur == null || valeur.isBlank() ? Optional.empty() : Optional.of(Path.of(valeur.trim()));
    }
}
