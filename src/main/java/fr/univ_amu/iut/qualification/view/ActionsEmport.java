package fr.univ_amu.iut.qualification.view;

import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.NotificateurModifiable;
import fr.univ_amu.iut.commun.view.SelecteurFichierJavaFx;
import fr.univ_amu.iut.commun.view.SelecteurFichierModifiable;
import fr.univ_amu.iut.passage.model.NatureDEntree;
import fr.univ_amu.iut.qualification.model.PlanDeReprise;
import fr.univ_amu.iut.qualification.model.ServiceEmport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.stage.Window;

/// Les deux gestes d'écran de l'emport (#4727) : emporter une nuit, et ouvrir un paquet reçu.
///
/// **Les trois dialogues arrivent en porteurs injectables**, comme partout ailleurs : sans cela un
/// geste qui commence par un sélecteur natif fige un test headless, et n'est donc testable nulle
/// part. C'est la raison d'être de [SelecteurFichierModifiable], et elle vaut ici mot pour mot.
public final class ActionsEmport {

    /// Ce que le paquet est, pour le sélecteur natif.
    private static final FiltreFichier PAQUET = new FiltreFichier("Paquet de nuit", "*.zip");

    private final ServiceEmport service;
    private final SelecteurFichierModifiable selecteur;
    private final ConfirmateurModifiable confirmateur = new ConfirmateurModifiable();
    private final NotificateurModifiable notificateur = new NotificateurModifiable((niveau, entete, message) -> {
        // Sans porteur défini, le geste reste silencieux plutôt que d'ouvrir un dialogue
        // qu'aucun parent n'a situé. L'application en pose un au câblage.
    });

    /// Pour les tests : sélecteurs remplaçables, sans fenêtre.
    ActionsEmport(ServiceEmport service) {
        this(service, () -> null);
    }

    /// @param service le parcours d'emport, livré par #4726
    /// @param fenetre la fenêtre où poser les sélecteurs natifs
    public ActionsEmport(ServiceEmport service, Supplier<Window> fenetre) {
        this.service = Objects.requireNonNull(service, "service");
        this.selecteur = new SelecteurFichierModifiable(new SelecteurFichierJavaFx(fenetre));
    }

    /// Le porteur du sélecteur, que les tests remplacent.
    SelecteurFichierModifiable selecteur() {
        return selecteur;
    }

    /// Le porteur de la confirmation, que les tests remplacent.
    public ConfirmateurModifiable confirmateur() {
        return confirmateur;
    }

    /// Le porteur du compte rendu, que le parent et les tests remplacent.
    public NotificateurModifiable notificateur() {
        return notificateur;
    }

    /// Emporte une nuit : désigner où, **annoncer ce que cela pèse**, confirmer, puis écrire.
    ///
    /// L'annonce précède l'écriture parce qu'un utilisateur qui confirme un emport sans en connaître
    /// le volume libère une place au jugé. Trois sorties sans écriture : l'annulation du sélecteur,
    /// le refus de l'annonce, et le refus du service.
    ///
    /// Ce dernier se dit en [NiveauNotification#AVERTISSEMENT] et non en silence. Le niveau est celui
    /// que l'énumération offre pour « l'action n'a pas eu lieu » : elle n'en porte que deux,
    /// délibérément, et un troisième serait un choix à faire à chaque appel sans que l'utilisateur y
    /// gagne rien.
    ///
    /// @param idPassage la nuit à emporter
    public void emporter(Long idPassage) {
        Optional<Path> destination =
                selecteur.enregistrerFichier("Emporter cette nuit pour relecture", "nuit.zip", PAQUET);
        if (destination.isEmpty()) {
            return;
        }
        try {
            ServiceEmport.EmportPrepare prepare = service.preparer(idPassage, destination.get());
            if (!confirmateur.confirmer(annonce(prepare))) {
                return;
            }
            long octets = service.ecrire(prepare);
            notificateur.notifier(
                    NiveauNotification.INFORMATION,
                    "Nuit emportée",
                    prepare.fichiers().size() + " séquence(s) écrites, " + kilooctets(octets) + ".");
        } catch (IllegalStateException refus) {
            notificateur.notifier(NiveauNotification.AVERTISSEMENT, "Emport impossible", refus.getMessage());
        } catch (IOException echec) {
            notificateur.notifier(NiveauNotification.AVERTISSEMENT, "Emport interrompu", echec.getMessage());
        }
    }

    /// Ouvre un paquet reçu : la sélection de l'expéditeur devient la nôtre, **figée**.
    ///
    /// @param identite l'identité du relecteur, apposée à l'ouverture
    public void ouvrirPaquetRecu(Optional<ProfilVigieChiro> identite) {
        Optional<Path> paquet = selecteur.choisirFichier("Ouvrir un paquet reçu", Optional.empty(), PAQUET);
        if (paquet.isEmpty()) {
            return;
        }
        try {
            ServiceEmport.BilanReprise bilan = service.reprendre(paquet.get(), identite);
            notificateur.notifier(
                    NiveauNotification.INFORMATION,
                    "Paquet ouvert",
                    bilan.sequences() + " séquence(s) à relire, signées « " + bilan.pseudoRelecteur() + " ».");
        } catch (IllegalStateException refus) {
            notificateur.notifier(NiveauNotification.AVERTISSEMENT, "Paquet refusé", refus.getMessage());
        } catch (IOException echec) {
            // Un mutant survit ici : provoquer une lecture rompue demanderait une archive corrompue
            // à mi-parcours, que le pendant en écriture couvre déjà. Le chemin jumeau, « Emport
            // interrompu », a son test.
            notificateur.notifier(NiveauNotification.AVERTISSEMENT, "Paquet illisible", echec.getMessage());
        }
    }

    /// Renvoie l'avis du relecteur : un paquet **signé de lui**, sans aucune séquence (#4744).
    ///
    /// @param idPassage la nuit relue
    /// @param pseudoJugeur le relecteur qui signe
    public void renvoyerAvis(Long idPassage, String pseudoJugeur) {
        Optional<Path> destination = selecteur.enregistrerFichier("Renvoyer mon avis", "avis.zip", PAQUET);
        if (destination.isEmpty()) {
            return;
        }
        try {
            ServiceEmport.BilanAvisRenvoye bilan = service.renvoyerAvis(idPassage, destination.get(), pseudoJugeur);
            notificateur.notifier(
                    NiveauNotification.INFORMATION,
                    "Avis renvoyé",
                    bilan.verdicts() + " verdict(s) signés « " + bilan.pseudoJugeur() + " ».");
        } catch (IllegalStateException refus) {
            notificateur.notifier(NiveauNotification.AVERTISSEMENT, "Avis non renvoyé", refus.getMessage());
        } catch (IOException echec) {
            notificateur.notifier(NiveauNotification.AVERTISSEMENT, "Envoi interrompu", echec.getMessage());
        }
    }

    /// Range un avis revenu à côté du nôtre (#4744, ADR 4517).
    ///
    /// **La confirmation ne se demande que si elle a lieu d'être.** Le service refuse un remplacement
    /// non confirmé en nommant le relecteur présent ; ce refus devient alors la question posée, plutôt
    /// qu'une confirmation systématique que l'utilisateur apprendrait à cliquer sans lire.
    public void importerAvis() {
        Optional<Path> avis = selecteur.choisirFichier("Reprendre un avis reçu", Optional.empty(), PAQUET);
        if (avis.isEmpty()) {
            return;
        }
        try {
            ServiceEmport.ImportPrepare prepare = service.preparerImport(avis.get());
            if (prepare.plan().refuse()) {
                notificateur.notifier(
                        NiveauNotification.AVERTISSEMENT,
                        "Avis non repris",
                        String.join(" ; ", prepare.plan().refus()));
                return;
            }
            if (prepare.plan().demandeConfirmation() && !confirmateur.confirmer(remplacement(prepare))) {
                return;
            }
            rendreCompte(service.appliquerImport(prepare, true));
        } catch (IllegalStateException refus) {
            notificateur.notifier(NiveauNotification.AVERTISSEMENT, "Avis non repris", refus.getMessage());
        } catch (IOException echec) {
            notificateur.notifier(NiveauNotification.AVERTISSEMENT, "Avis illisible", echec.getMessage());
        }
    }

    /// Ce que la confirmation d'un second avis annonce : qui serait remplacé, et ce qui serait perdu.
    private static String remplacement(ServiceEmport.ImportPrepare prepare) {
        PlanDeReprise.AvisDejaPresent present = prepare.plan().avisDejaPresent();
        return "L'avis de « " + present.pseudo() + " » et ses " + present.verdicts()
                + " verdict(s) seraient définitivement remplacés par ceux de « "
                + prepare.avis().pseudoRelecteur() + " ». Continuer ?";
    }

    private void rendreCompte(ServiceEmport.BilanImportAvis bilan) {
        notificateur.notifier(
                NiveauNotification.INFORMATION,
                "Avis repris",
                bilan.verdicts() + " verdict(s) de « " + bilan.pseudoRelecteur() + " » rangés à côté des vôtres.");
    }

    /// Ce que la confirmation annonce : combien de séquences, et ce que cela pèsera.
    private static String annonce(ServiceEmport.EmportPrepare prepare) {
        long sequences = prepare.plan().octetsParNature().getOrDefault(NatureDEntree.SEQUENCE, 0L);
        return prepare.fichiers().size() + " séquence(s) seront écrites, soit "
                + kilooctets(prepare.plan().octetsEstimes())
                + " (dont " + kilooctets(sequences) + " d'audio). Emporter cette nuit ?";
    }

    /// Le volume dans l'unité qui se lit.
    ///
    /// **Deux mutants survivent, et c'est assumé.** La borne `< 1024` sépare « 1023 o » de « 1 Ko » :
    /// un test à l'octet près éprouverait l'arrondi, pas ce que l'utilisateur décide. La division,
    /// elle, est tenue : un test constate que 5 000 octets s'annoncent « 4 Ko », là où une
    /// multiplication dirait 5 120 000.
    ///
    /// @param octets le volume mesuré
    /// @return le volume en octets sous le kilooctet, en kilooctets au-delà
    private static String kilooctets(long octets) {
        return octets < 1024 ? octets + " o" : (octets / 1024) + " Ko";
    }
}
