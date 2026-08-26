package fr.univ_amu.iut.commun.persistence;

import fr.univ_amu.iut.commun.model.EspaceDisque;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/// Remet en place les dossiers de son d'une **sauvegarde complète**, et corrige la base pour qu'elle
/// les désigne là où ils sont réellement (#2727).
///
/// La restauration déversait auparavant les dossiers à la racine du workspace sans toucher aux
/// `root_path` : la base restaurée continuait de pointer vers les chemins d'origine, qui peuvent ne
/// plus exister sur la machine cible. **L'ordre compte** - la confrontation de chaque dossier à
/// l'inventaire du manifeste a lieu **en premier**, et une seule discordance suffit pour que rien ne
/// soit écrit, ni la base ni les dossiers en place.
class RestaurationComplete {

    private static final String SOUS_DOSSIER_SESSIONS = "sessions";

    private final SourceDeDonnees source;
    private final UniteDeTravail uniteDeTravail;
    private final BasculeRacines bascules;
    private final EspaceDisque espaceDisque;

    RestaurationComplete(SourceDeDonnees source, EspaceDisque espaceDisque) {
        this.source = source;
        this.espaceDisque = espaceDisque;
        this.uniteDeTravail = new UniteDeTravail(source);
        this.bascules = new BasculeRacines(source.workspace());
    }

    /// Confronte chaque dossier de la sauvegarde à l'inventaire que le manifeste en donne. Une
    /// discordance est un **refus**, avant que rien n'ait été touché.
    ///
    /// @throws DataAccessException si un dossier manque ou ne correspond plus à son inventaire
    void verifierLaSauvegarde(Path dossierBackup, ManifesteSauvegarde manifeste) {
        for (RacineSauvegardee attendue : manifeste.racines()) {
            Path copie = dossierBackup.resolve(SOUS_DOSSIER_SESSIONS).resolve(attendue.identifiant());
            if (!Files.isDirectory(copie)) {
                throw new RefusAvantEcriture(
                        "La sauvegarde annonce le dossier « " + attendue.identifiant()
                                + " » (venu de " + attendue.cheminOrigine() + ") mais il n'y est pas."
                                + " Restauration annulée, rien n'a été touché.",
                        null);
            }
            verifierInventaire(copie, attendue, Moment.DANS_LA_SAUVEGARDE);
        }
    }

    /// Replace les dossiers et corrige la base. À appeler **après** [#verifierLaSauvegarde] et après
    /// la restauration de la base : les `root_path` réécrits sont ceux de la base restaurée.
    ///
    /// Les copies se font **à côté** de leur destination, sous un suffixe `.en-cours`
    /// ([BasculeRacines]), et rien ne prend sa place avant que **toutes** aient été copiées et
    /// vérifiées : une panne d'écriture ne laisse alors que des temporaires, balayés dans la foulée
    /// (#3514). **Ce n'est pas de l'atomicité** - basculer trois dossiers, ce sont trois renommages.
    /// Ce qu'on gagne est de ramener la fenêtre d'une copie complète à une suite de renommages.
    BilanRestauration replacer(Path dossierBackup, ManifesteSauvegarde manifeste) {
        // AVANT toute réécriture : une fois les racines déplacées, plus aucune ne correspondrait à
        // son origine dans le manifeste, et la nuit qu'on vient de restaurer serait annoncée absente.
        List<String> absentes = absentesDuManifeste(manifeste);
        RegimeRestauration regime =
                BesoinDePlace.de(manifeste, bascules::destinationPour).regimePour(espaceDisque);
        List<PlacementRacine> placements = regime == RegimeRestauration.ENSEMBLE
                ? toutEtalerPuisBasculer(dossierBackup, manifeste)
                : uneNuitALaFois(dossierBackup, manifeste);
        reecrireLesChemins(placements);
        return new BilanRestauration(true, placements, absentes, regime);
    }

    /// Le régime nominal : rien ne prend sa place définitive avant que **toutes** les nuits aient été
    /// étalées et vérifiées. Une panne ne laisse alors que des temporaires, balayés dans la foulée.
    private List<PlacementRacine> toutEtalerPuisBasculer(Path dossierBackup, ManifesteSauvegarde manifeste) {
        List<BasculeRacines.EnAttente> attentes = new ArrayList<>();
        try {
            for (RacineSauvegardee emportee : manifeste.racines()) {
                // Enregistrée AVANT les vérifications : elles peuvent refuser, et le temporaire existe
                // déjà. L'appelant ne le nettoierait jamais s'il ne le connaissait pas.
                BasculeRacines.EnAttente attente = etaler(dossierBackup, emportee);
                attentes.add(attente);
                verifier(attente, emportee);
            }
            List<PlacementRacine> placements = new ArrayList<>();
            for (BasculeRacines.EnAttente attente : attentes) {
                placements.add(attente.basculer());
            }
            return placements;
        } catch (RuntimeException echec) {
            attentes.forEach(BasculeRacines.EnAttente::abandonner);
            throw echec;
        }
    }

    /// Le régime dégradé, quand la place ne permet pas d'étaler tout le monde : une nuit est étalée,
    /// vérifiée, basculée, puis on passe à la suivante (#3563).
    ///
    /// **Dès la première bascule, « rien n'a été touché » cesse d'être vrai.** Un refus survenu
    /// ensuite ne peut donc plus se présenter comme un refus : il deviendrait un code de sortie qui
    /// promet un état intact au-dessus d'un état mixte. Il est requalifié en incident, ce qu'il est.
    private List<PlacementRacine> uneNuitALaFois(Path dossierBackup, ManifesteSauvegarde manifeste) {
        List<PlacementRacine> placements = new ArrayList<>();
        for (RacineSauvegardee emportee : manifeste.racines()) {
            BasculeRacines.EnAttente attente = null;
            try {
                attente = etaler(dossierBackup, emportee);
                verifier(attente, emportee);
                placements.add(attente.basculer());
            } catch (RuntimeException echec) {
                if (attente != null) {
                    attente.abandonner();
                }
                throw placements.isEmpty() ? echec : etatMixte(placements, echec);
            }
        }
        return placements;
    }

    private BasculeRacines.EnAttente etaler(Path dossierBackup, RacineSauvegardee emportee) {
        Path copie = dossierBackup.resolve(SOUS_DOSSIER_SESSIONS).resolve(emportee.identifiant());
        return bascules.preparer(copie, emportee.cheminOrigine());
    }

    private void verifier(BasculeRacines.EnAttente attente, RacineSauvegardee emportee) {
        verifierInventaire(attente.temporaire(), emportee, Moment.UNE_FOIS_REMIS_EN_PLACE);
        bascules.refuserSiLaDestinationPorteAutreChose(attente);
    }

    private static DataAccessException etatMixte(List<PlacementRacine> deja, RuntimeException cause) {
        return new DataAccessException(
                "La restauration s'est arrêtée après avoir remis " + deja.size()
                        + " nuit(s) en place : la place ne permettait pas de toutes les préparer d'abord."
                        + " L'état local est partiel, et la sauvegarde reste intacte pour recommencer.",
                cause);
    }

    /// Restauration d'une sauvegarde **sans manifeste** : le comportement d'avant #2726, faute de
    /// savoir d'où venaient les dossiers. Ils reviennent sous leur nom de dossier, à la racine du
    /// workspace, et la base n'est pas corrigée.
    BilanRestauration replacerSansManifeste(Path dossierBackup) {
        Path dossierSessions = dossierBackup.resolve(SOUS_DOSSIER_SESSIONS);
        if (!Files.isDirectory(dossierSessions)) {
            return BilanRestauration.sansManifeste();
        }
        try (Stream<Path> sessions = Files.list(dossierSessions)) {
            for (Path sessionSauvegardee : (Iterable<Path>) sessions::iterator) {
                if (Files.isDirectory(sessionSauvegardee)) {
                    ArborescenceFichiers.copier(
                            sessionSauvegardee,
                            source.workspace()
                                    .racine()
                                    .resolve(sessionSauvegardee.getFileName().toString()));
                }
            }
        } catch (IOException echec) {
            throw new DataAccessException(
                    "Restauration des dossiers de session impossible depuis " + dossierBackup, echec);
        }
        return BilanRestauration.sansManifeste();
    }

    /// Quand la vérification a lieu, ce qui décide de sa **nature** (#3146).
    ///
    /// Confrontée à la sauvegarde, elle précède toute écriture : une discordance est un **refus**, et
    /// l'état local est intact. Confrontée à la destination, elle suit une copie : une discordance est
    /// un **incident**, et l'état est celui d'une copie à moitié faite.
    ///
    /// Le même code de vérification sert les deux ; sans cette distinction, il faudrait choisir une
    /// nature pour les deux et se tromper une fois sur deux.
    private enum Moment {
        DANS_LA_SAUVEGARDE("dans la sauvegarde", true),
        UNE_FOIS_REMIS_EN_PLACE("une fois remis en place", false);

        private final String enClair;
        private final boolean avantToutEcriture;

        Moment(String enClair, boolean avantToutEcriture) {
            this.enClair = enClair;
            this.avantToutEcriture = avantToutEcriture;
        }

        DataAccessException discordance(String message) {
            return avantToutEcriture ? new RefusAvantEcriture(message, null) : new DataAccessException(message, null);
        }
    }

    private static void verifierInventaire(Path dossier, RacineSauvegardee attendue, Moment moment) {
        InventaireDossier constate;
        try {
            constate = InventaireDossier.de(dossier);
        } catch (IOException echec) {
            throw new DataAccessException("Dossier de son illisible : " + dossier, echec);
        }
        if (constate.fichiers() == attendue.fichiers()
                && constate.octets() == attendue.octets()
                && constate.empreinte().equals(attendue.empreinte())) {
            return;
        }
        throw moment.discordance("Le dossier de son venu de " + attendue.cheminOrigine()
                + " ne correspond pas à ce que la sauvegarde annonce, " + moment.enClair + " : "
                + attendue.fichiers() + " fichier(s) et " + attendue.octets() + " octet(s) attendus, "
                + constate.fichiers() + " et " + constate.octets() + " trouvés.");
    }

    /// Corrige **tous les chemins persistés** des sessions qui ont changé de place, en une seule
    /// transaction : une correction à moitié faite laisserait une base dont une partie désigne des
    /// fichiers présents et l'autre des fichiers absents, sans rien pour distinguer les deux.
    ///
    /// Réécrire la seule racine ne suffit pas, et c'est le piège que cette méthode a d'abord eu :
    /// chaque original, chaque séquence, le journal, le relevé et le CSV Tadarida portent leur
    /// chemin **absolu**. La base paraissait corrigée, et l'application ne trouvait plus un fichier.
    private void reecrireLesChemins(List<PlacementRacine> placements) {
        List<PlacementRacine> deplacees =
                placements.stream().filter(PlacementRacine::deplacee).toList();
        if (deplacees.isEmpty()) {
            return;
        }
        uniteDeTravail.executer(connexion -> {
            for (PlacementRacine placement : deplacees) {
                Path ancienne = Path.of(placement.origine());
                Path nouvelle = Path.of(placement.destination());
                for (SessionARelocaliser session : sessionsSous(connexion, placement.origine())) {
                    ReecritureRacineSession.reenraciner(connexion, session, ancienne, nouvelle);
                }
            }
        });
    }

    /// Les sessions dont la racine est `racine`. Plusieurs peuvent la partager : le dossier d'une
    /// nuit peut porter plus d'une session d'enregistrement.
    private static List<SessionARelocaliser> sessionsSous(Connection cx, String racine) throws SQLException {
        List<SessionARelocaliser> sessions = new ArrayList<>();
        try (PreparedStatement ps =
                cx.prepareStatement("SELECT id, passage_id FROM recording_session WHERE root_path = ?")) {
            ps.setString(1, racine);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long idPassage = rs.getLong(2);
                    sessions.add(new SessionARelocaliser(rs.getLong(1), rs.wasNull() ? null : idPassage));
                }
            }
        }
        return sessions;
    }

    /// Racines que la base connaît mais que le manifeste n'a pas : elles étaient inaccessibles au
    /// moment de la sauvegarde (#1346), leur dossier n'a donc pas été emporté et reste introuvable.
    private List<String> absentesDuManifeste(ManifesteSauvegarde manifeste) {
        Set<String> emportees = new HashSet<>();
        for (RacineSauvegardee racine : manifeste.racines()) {
            emportees.add(racine.cheminOrigine());
        }
        List<String> absentes = new ArrayList<>();
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT DISTINCT root_path FROM recording_session WHERE root_path IS NOT NULL")) {
            while (rs.next()) {
                String racine = rs.getString(1);
                if (!emportees.contains(racine)) {
                    absentes.add(racine);
                }
            }
        } catch (SQLException echec) {
            throw new DataAccessException("Lecture des racines de session impossible", echec);
        }
        return absentes;
    }
}
