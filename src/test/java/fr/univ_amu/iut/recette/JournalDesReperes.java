package fr.univ_amu.iut.recette;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// Le fichier de repères d'une séance filmée : quand chaque cas s'est joué (#3774, EPIC #3667).
///
/// ## Le problème qu'il sert à résoudre
///
/// Un test sait à quelle **heure** il commence ; le film, lui, se compte en secondes depuis son
/// début. Sans pont entre les deux, un film de séance reste un bloc que personne ne regardera pour
/// trancher un cas. Ce journal est ce pont : il consigne des instants d'horloge, et c'est le montage
/// qui les ramènera à des positions dans la vidéo.
///
/// ## L'horloge, et pourquoi c'est celle-là
///
/// Les instants sont des **millisecondes depuis l'époque** ([System#currentTimeMillis()]), la même
/// grandeur que `date +%s%3N` côté script. Surtout pas [System#nanoTime()], qui ne se compare à
/// rien hors de cette JVM : le montage n'aurait alors aucun moyen de s'y raccrocher, et le décalage
/// ne se verrait pas - il produirait des extraits plausibles pris au mauvais endroit.
///
/// ## Pourquoi on écrit à chaque événement, sans tampon
///
/// Une séance filmée s'interrompt : un test se bloque, on coupe. Un journal tamponné perdrait alors
/// tout ce qui précède, c'est-à-dire précisément ce qu'on voulait garder. Chaque ligne part donc en
/// ajout immédiat, et une séance tuée laisse un journal tronqué mais exploitable.
///
/// ## Format
///
/// Un TSV à quatre colonnes, précédé d'une ligne de commentaire pour qui l'ouvre à la main :
///
/// ```text
/// # repères de séance (#3774) : epoch_ms	borne	test	cas
/// 1755188400123	debut	ConnexionModaleViewTest.ouvrir_site	S1-04
/// 1755188404567	fin	ConnexionModaleViewTest.ouvrir_site	S1-04
/// ```
///
/// Un test peut citer plusieurs cas : ils sont alors séparés par des virgules dans la dernière
/// colonne. C'est le montage qui en tire un index par cas, le clip restant taillé sur le **test**,
/// puisque c'est ce que la JVM sait borner.
public final class JournalDesReperes {

    /// La propriété qui dit où écrire.
    ///
    /// Absente ou vide, rien n'est journalisé : un `mvn test` ordinaire ne produit aucun fichier et
    /// ne paie rien. Seul le profil `recette-filmee` la pose.
    public static final String PROPRIETE = "recette.reperes";

    private static final String ENTETE = "# repères de séance (#3774) : epoch_ms\tborne\ttest\tcas";

    /// La borne d'un passage : le montage a besoin des deux pour tailler un extrait.
    public enum Borne {
        DEBUT,
        FIN;

        String ecrite() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Path fichier;

    private JournalDesReperes(Path fichier) {
        this.fichier = fichier;
    }

    /// Le journal désigné par [#PROPRIETE], ou rien si elle n'est pas posée.
    public static Optional<JournalDesReperes> depuisLaPropriete() {
        String chemin = System.getProperty(PROPRIETE, "");
        return chemin.isBlank() ? Optional.empty() : Optional.of(vers(Path.of(chemin)));
    }

    /// Ouvre le journal, en créant son dossier et son en-tête au besoin.
    public static JournalDesReperes vers(Path fichier) {
        try {
            Path dossier = fichier.toAbsolutePath().getParent();
            if (dossier != null) {
                Files.createDirectories(dossier);
            }
            if (Files.notExists(fichier)) {
                Files.writeString(fichier, ENTETE + System.lineSeparator());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Journal de repères impossible à ouvrir : " + fichier, e);
        }
        return new JournalDesReperes(fichier);
    }

    /// Consigne une borne.
    ///
    /// @param borne début ou fin du passage
    /// @param test le test qui se joue, `Classe.methode`
    /// @param cas les cas de recette qu'il cite
    /// @param instantMs l'instant, en millisecondes depuis l'époque
    public void note(Borne borne, String test, List<String> cas, long instantMs) {
        String ligne = "%d\t%s\t%s\t%s%s"
                .formatted(instantMs, borne.ecrite(), test, String.join(",", cas), System.lineSeparator());
        try {
            Files.writeString(fichier, ligne, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Repère impossible à écrire dans " + fichier, e);
        }
    }

    /// Le fichier écrit, pour que les tests puissent le relire.
    public Path fichier() {
        return fichier;
    }
}
