package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.VersionApplication;
import fr.univ_amu.iut.commun.model.Workspace;
import java.util.Objects;
import javafx.stage.Window;

/// Entrée ☰ **« À propos »** (#2108) : dit quelle version tourne, et sur quoi.
///
/// Elle existe pour un usage précis : **renseigner un signalement d'anomalie**. Jusqu'ici,
/// l'application ne connaissait pas sa propre version, donc un utilisateur qui rapportait un défaut
/// ne pouvait pas dire laquelle il utilisait - et le corriger revenait à deviner. C'est pourquoi
/// cette entrée voisine « Ouvrir le dossier des journaux » dans le groupe MAINTENANCE : les deux
/// servent le même geste, et on les cherche au même moment.
///
/// Le contenu suit cette intention. Chaque ligne est là parce qu'elle change le diagnostic : la
/// version dit ce qui tourne, le JDK et le système expliquent les défauts qui ne se reproduisent que
/// chez certains, et le dossier de travail situe la base et les journaux qu'on demandera ensuite.
///
/// Le compte rendu passe par le port [Notificateur] plutôt que par un `Alert` en dur, pour la raison
/// qui a motivé ce port : un `showAndWait()` **fige** un test TestFX headless, et l'entrée ne serait
/// alors couverte nulle part (cf. ADR 0010).
public final class ActionAPropos implements ActionMenu {

    /// Mentions conventionnelles d'un « À propos ». Elles vivent ici plutôt que dans le manifeste :
    /// ce sont des constantes de produit, pas des propriétés d'empaquetage, et les inscrire au
    /// manifeste ferait dépendre l'affichage d'un jar - donc les rendrait vides en développement,
    /// exactement là où on relit ce dialogue le plus souvent.
    private static final String EDITEUR = "Sébastien Nedjar";

    /// GPLv3 : conséquence de la dépendance Gluon Maps (carte interactive), elle-même GPL. La citer
    /// n'est pas une politesse - la licence impose que le destinataire d'un binaire sache sous
    /// quelles conditions il le reçoit et où en obtenir les sources.
    private static final String LICENCE = "GNU General Public License v3.0";

    private static final String DEPOT = "https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion";

    /// Fenêtre propriétaire du dialogue, posée au clic : l'action la lit **paresseusement**, car elle
    /// n'existe pas encore quand l'entrée de menu est construite (même montage qu'[ActionPurger]).
    private Window proprietaire;

    private final VersionApplication version;
    private final Workspace workspace;
    private final NotificateurModifiable notificateur =
            new NotificateurModifiable(new NotificationDialogue(() -> proprietaire));

    @Inject
    ActionAPropos(VersionApplication version, Workspace workspace) {
        this.version = Objects.requireNonNull(version, "version");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
    }

    /// Porteur de compte rendu exposé aux tests : `notificateur().definir(double)`.
    NotificateurModifiable notificateur() {
        return notificateur;
    }

    @Override
    public GroupeMenu groupe() {
        return GroupeMenu.MAINTENANCE;
    }

    @Override
    public int ordre() {
        return 30;
    }

    @Override
    public String libelle() {
        return "À propos";
    }

    @Override
    public String iconeLiteral() {
        // `fas-info-circle` et non `fas-circle-info` : le second est le nom FontAwesome **6**, et le
        // dépôt embarque le pack Ikonli FontAwesome **5**. Un nom absent du pack ne fait pas rougir
        // la compilation - il lève au chargement du FXML, donc à l'ouverture de l'écran.
        return "fas-info-circle";
    }

    @Override
    public void executer(Window proprietaire) {
        this.proprietaire = proprietaire;
        notificateur.notifier(NiveauNotification.INFORMATION, "VigieChiro - compagnon PR", """
                Version : %s

                Compagnon du protocole Vigie-Chiro : préparation et dépôt des nuits
                d'enregistrement acoustique de chiroptères.

                %s
                Licence : %s
                Code source : %s

                Java %s
                Système : %s (%s)
                Dossier de travail : %s

                Les quatre dernières lignes sont utiles pour signaler une anomalie.""".formatted(
                        version.libelle(),
                        EDITEUR,
                        LICENCE,
                        DEPOT,
                        System.getProperty("java.version"),
                        System.getProperty("os.name"),
                        System.getProperty("os.arch"),
                        workspace.racine()));
    }
}
