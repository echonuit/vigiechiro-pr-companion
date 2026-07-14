package fr.univ_amu.iut.connexion.model;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.api.FournisseurToken;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Workspace;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// Stockage local de la **connexion VigieChiro** (#727) : le token (fourni à
/// [ClientVigieChiro][fr.univ_amu.iut.commun.api.ClientVigieChiro]
/// via [FournisseurToken]) et l'identité mise en cache, persistés dans un petit fichier JSON
/// `connexion.json` à la racine du workspace (hors-git, comme la base SQLite).
///
/// Le token de la plateforme expire après ~14 jours : au-delà de [#PEREMPTION_JOURS] depuis son
/// enregistrement, il est considéré **périmé** (token et identité renvoyés vides), ce qui ramène l'app
/// à « non connecté » sans appel réseau. Se déconnecter efface le fichier. Objet de données pur
/// (aucune dépendance JavaFX ni JDBC) ; l'[Horloge] injectée rend la péremption testable.
@Singleton
public final class StockageConnexion implements FournisseurToken {

    /// Durée de validité locale du token, alignée sur la péremption de la plateforme (14 jours).
    static final long PEREMPTION_JOURS = 14;

    private static final String FICHIER = "connexion.json";
    private static final Gson GSON = new Gson();

    /// Permissions POSIX du fichier de connexion : lecture/écriture pour le seul propriétaire (`600`).
    /// Le token ne doit pas être lisible par les autres comptes de la machine.
    private static final Set<PosixFilePermission> PERMS_PRIVEES =
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Path fichier;
    private final Horloge horloge;

    /// `@Inject` + `@Singleton` : le stockage se laisse construire par Guice **même dans un injecteur
    /// partiel** qui ne charge pas `ConnexionModule` (outils de capture, tests de module). Sans cela, toute
    /// feature qui en dépend — depuis #1417, la vue audio le consulte pour dire « Vous » dans le fil de
    /// discussion — casserait ces injecteurs, alors que `Workspace` et `Horloge` y sont toujours liés.
    /// Là où `ConnexionModule` est chargé, son `@Provides` fait foi.
    @Inject
    public StockageConnexion(Workspace workspace, Horloge horloge) {
        this.fichier = Objects.requireNonNull(workspace, "workspace").racine().resolve(FICHIER);
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    @Override
    public Optional<String> token() {
        return sessionValide().map(Session::token);
    }

    /// Identité mise en cache (`GET /moi`), ou vide si non connecté / périmé / pas encore récupérée.
    public Optional<ProfilVigieChiro> profil() {
        return sessionValide()
                .filter(session -> session.id() != null)
                .map(session -> new ProfilVigieChiro(session.id(), session.pseudo(), session.role()));
    }

    /// `true` si un token valide (non périmé) est enregistré.
    public boolean estConnecte() {
        return token().isPresent();
    }

    /// Enregistre le `token` et l'identité `profil` (qui peut être `null` tant que `GET /moi` n'a pas
    /// répondu), horodatés au jour courant. Écrase toute connexion précédente.
    public void enregistrer(String token, ProfilVigieChiro profil) {
        Session session = new Session(
                Objects.requireNonNull(token, "token"),
                horloge.aujourdhui().toString(),
                profil == null ? null : profil.id(),
                profil == null ? null : profil.pseudo(),
                profil == null ? null : profil.role());
        try {
            Files.createDirectories(fichier.getParent());
            Files.writeString(fichier, GSON.toJson(session));
            restreindrePermissions();
        } catch (IOException echec) {
            throw new UncheckedIOException("Impossible d'enregistrer la connexion : " + fichier, echec);
        }
    }

    /// Restreint le fichier de connexion au seul propriétaire (POSIX `600`) après écriture : le token est
    /// un secret, il ne doit pas être lisible par les autres comptes de la machine. **Sans objet** sur un
    /// système de fichiers non POSIX (Windows), où [PosixFileAttributeView] est absente : le fichier reste
    /// alors protégé par les ACL du profil utilisateur.
    private void restreindrePermissions() throws IOException {
        PosixFileAttributeView vue = Files.getFileAttributeView(fichier, PosixFileAttributeView.class);
        if (vue != null) {
            vue.setPermissions(PERMS_PRIVEES);
        }
    }

    /// Efface la connexion locale (déconnexion). Idempotent.
    public void effacer() {
        try {
            Files.deleteIfExists(fichier);
        } catch (IOException echec) {
            throw new UncheckedIOException("Impossible d'effacer la connexion : " + fichier, echec);
        }
    }

    private Optional<Session> sessionValide() {
        return lire().filter(this::nonPerimee);
    }

    private boolean nonPerimee(Session session) {
        try {
            LocalDate limite = LocalDate.parse(session.date()).plusDays(PEREMPTION_JOURS);
            return !horloge.aujourdhui().isAfter(limite);
        } catch (RuntimeException dateIllisible) {
            return false;
        }
    }

    private Optional<Session> lire() {
        if (!Files.isRegularFile(fichier)) {
            return Optional.empty();
        }
        try {
            Session session = GSON.fromJson(Files.readString(fichier), Session.class);
            return session == null || session.token() == null ? Optional.empty() : Optional.of(session);
        } catch (IOException | RuntimeException illisible) {
            return Optional.empty();
        }
    }

    /// Forme persistée (JSON) : token, date d'enregistrement (ISO) et identité mise en cache.
    private record Session(String token, String date, String id, String pseudo, String role) {}
}
