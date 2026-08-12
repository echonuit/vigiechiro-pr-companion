package fr.univ_amu.iut.maj.model;

import fr.univ_amu.iut.commun.model.VersionApplication;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/// Fabrique une [VersionApplication] qui se croit **empaquetée**, ce qu'aucun test ne peut obtenir
/// autrement : le numéro vient de `Implementation-Version`, que seul le manifeste d'un jar porte.
///
/// Extrait de `VerificateurMiseAJourTest` quand un second test en a eu besoin (#3616). Recopier la
/// recette aurait dupliqué sa dette - une quinzaine de lignes de plomberie que personne ne relit
/// deux fois - et les deux copies auraient divergé au premier ajustement.
public final class JarDeVersion {

    private JarDeVersion() {
        // Fabrique.
    }

    /// Rend une [VersionApplication] qui rapporte `version`, en empaquetant réellement un jar.
    public static VersionApplication annoncant(Path dossier, String version) throws Exception {
        String classe = "fr.univ_amu.iut.commun.model.VersionApplication";
        String chemin = classe.replace('.', '/') + ".class";

        Manifest manifeste = new Manifest();
        manifeste.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifeste.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, version);

        Path jar = dossier.resolve("essai-" + version.replace('.', '_') + ".jar");
        try (InputStream source = VersionApplication.class.getResourceAsStream("/" + chemin);
                JarOutputStream sortie = new JarOutputStream(Files.newOutputStream(jar), manifeste)) {
            sortie.putNextEntry(new JarEntry(chemin));
            sortie.write(Objects.requireNonNull(source, chemin).readAllBytes());
            sortie.closeEntry();
        }

        try (URLClassLoader chargeur = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            return new VersionApplication(Class.forName(classe, false, chargeur));
        }
    }
}
