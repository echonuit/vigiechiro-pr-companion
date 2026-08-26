package fr.univ_amu.iut.commun.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/// « Aucun autre compte ne peut lire ce fichier » - la **propriété**, pas le mécanisme (#3778).
///
/// Le fichier en jeu est `connexion.json` : il porte le **jeton VigieChiro**. [EcritureAtomique]
/// affirmait qu'il restait protégé sous Windows par les ACL du profil, sans que rien ne l'éprouve.
///
/// La mesure, sous Windows Server 2025, dit qu'elle avait raison : le fichier écrit par
/// [EcritureAtomique#ecrireSecret] porte exactement **trois** entrées `ALLOW`, son propriétaire,
/// `NT AUTHORITY\SYSTEM` et `BUILTIN\Administrators`. C'est l'équivalent de `600` sous POSIX, où
/// **root** lit aussi ce qu'il veut.
///
/// À savoir : la protection est **héritée** du dossier de profil, pas posée par le produit. Elle est
/// réelle, et elle peut changer sans que rien ne le dise. D'où cette lecture, et le test qui s'en
/// sert.
///
/// Une propriété plutôt qu'un `assumeTrue` : un saut hors POSIX est honnête, mais il laisse la
/// propriété non vérifiée là où elle compte le plus. Exprimée ainsi, elle a un sens sur les deux
/// systèmes, et le conditionnel vit dans un seul endroit éprouvé - même raison que `GestesFichiers`
/// (#3525), `TailleFichier` (#3627) et `CouleurCli` (#3738).
public final class ProtectionFichier {

    /// Les comptes qui, sous Windows, ont accès à **tout** de toute façon - l'équivalent de `root`.
    /// Les compter comme une fuite rendrait la propriété infalsifiable, donc inutile.
    private static final Set<String> ADMINISTRATION =
            Set.of("NT AUTHORITY\\SYSTEM", "BUILTIN\\Administrators", "BUILTIN\\Administrateurs");

    private ProtectionFichier() {}

    /// Le fichier est-il fermé aux **autres comptes** de la machine ?
    ///
    /// @throws IOException si les attributs ne se lisent pas ; une protection qu'on ne peut pas lire
    ///     ne se déclare pas acquise
    public static boolean restreinteAuProprietaire(Path fichier) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(fichier, PosixFileAttributeView.class);
        if (posix != null) {
            Set<PosixFilePermission> permissions = posix.readAttributes().permissions();
            return permissions.stream().allMatch(p -> p.name().startsWith("OWNER_"));
        }

        AclFileAttributeView acl = Files.getFileAttributeView(fichier, AclFileAttributeView.class);
        if (acl == null) {
            // Ni POSIX ni ACL : on ne SAIT pas. Rendre `true` ici annoncerait une protection depuis
            // une ignorance, ce qui est exactement le faux vert que cette classe existe pour éviter.
            throw new IOException("Ni permissions POSIX ni ACL sur « " + fichier + " » : la protection du fichier "
                    + "ne peut pas être établie sur ce système de fichiers.");
        }
        String proprietaire = acl.getOwner().getName();
        return acl.getAcl().stream()
                .filter(entree -> entree.type() == AclEntryType.ALLOW)
                .map(AclEntry::principal)
                .map(java.security.Principal::getName)
                .allMatch(nom -> nom.equals(proprietaire) || ADMINISTRATION.contains(nom));
    }
}
