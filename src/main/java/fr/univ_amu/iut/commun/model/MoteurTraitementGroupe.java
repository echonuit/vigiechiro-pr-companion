package fr.univ_amu.iut.commun.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/// Applique une [ActionGroupee] à une suite de passages (#2357), **un à la fois**, et rend compte de
/// chacun.
///
/// ## Séquentiel, délibérément
///
/// Le moteur ne parallélise **rien**. Les gestes qu'il enchaîne parallélisent déjà en interne quand
/// c'est utile — le dépôt téléverse cinq unités de front, plafond calqué sur le front web. Lancer N
/// passages simultanément multiplierait ce plafond par N et transformerait un confort en rafale que
/// le serveur rejette. Le parallélisme reste donc **à l'intérieur** d'un passage, jamais entre eux.
///
/// ## L'annulation s'arrête *entre* deux passages
///
/// Le jeton est consulté **avant** chaque passage, jamais pendant. Un passage commencé va donc au
/// bout, et tout passage non commencé est rendu [IssueTraitement.Statut#NON_TRAITE] : chacun est soit
/// dans son état d'avant, soit dans son état d'après, **jamais entre les deux**. C'est le contrat qui
/// rend un lot interrompu reprenable — et il est tenu par construction, pas par vigilance.
///
/// ## Un échec n'arrête pas le lot
///
/// Une action qui lève est enregistrée en échec **avec son motif**, et le lot continue : rentrer de
/// terrain avec six cartes et voir cinq passages abandonnés parce que le premier a échoué serait le
/// contraire du service rendu.
///
/// ## Le motif d'échec est **rédigé par la surface**
///
/// Un refus a deux moitiés (ADR 2635) : le **fait**, que le modèle énonce, et le **geste** qui le lève,
/// que seule la surface connaît. Prendre `getMessage()` ne garde que la première - un jeton expiré au
/// septième passage donnerait treize lignes disant « l'application n'est pas connectée » et aucune
/// disant où se reconnecter, là où la même erreur sur une seule nuit le dit.
///
/// Le moteur ne formate donc rien lui-même : il applique la rédaction qu'on lui a donnée, à la fois au
/// journal et au compte rendu. L'application lui passe `commun.viewmodel.GesteAttendu::message`, la
/// ligne de commande passerait `cli.GesteAttenduCli::message`, et un appelant qui n'en veut pas garde
/// le fait seul.
public final class MoteurTraitementGroupe {

    /// Journal muet, pour les appelants qui n'en veulent pas (tests, CLI silencieux).
    private static final Consumer<String> SANS_JOURNAL = ligne -> {};

    private final Function<RuntimeException, String> redactionEchec;

    /// Sans rédaction de surface : le fait seul, ce qui reste juste, seulement moins guidant.
    public MoteurTraitementGroupe() {
        this(MoteurTraitementGroupe::faitSeul);
    }

    /// @param redactionEchec ce qui transforme une exception en la phrase que l'observateur lira ;
    ///     `GesteAttendu::message` côté application
    public MoteurTraitementGroupe(Function<RuntimeException, String> redactionEchec) {
        this.redactionEchec = Objects.requireNonNull(redactionEchec, "redactionEchec");
    }

    /// Exécute `action` sur chaque cible, sans journal.
    public ResultatTraitementGroupe executer(ActionGroupee action, List<CiblePassage> cibles, JetonAnnulation jeton) {
        return executer(action, cibles, jeton, SANS_JOURNAL);
    }

    /// Exécute `action` sur chaque cible et rend le sort de chacune.
    ///
    /// Chaque ligne remise à `journal` est **préfixée du passage concerné** : sans cela, un lot
    /// interrompu est indiagnostiquable — on sait qu'il a échoué, pas où.
    ///
    /// @param jeton consulté avant chaque passage ; une fois annulé, les passages restants sont rendus
    ///     `NON_TRAITE` sans être touchés
    /// @return une issue par cible, dans l'ordre de soumission
    public ResultatTraitementGroupe executer(
            ActionGroupee action, List<CiblePassage> cibles, JetonAnnulation jeton, Consumer<String> journal) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(cibles, "cibles");
        Objects.requireNonNull(jeton, "jeton");
        Objects.requireNonNull(journal, "journal");

        List<IssueTraitement> issues = new ArrayList<>();
        boolean interrompu = false;
        for (CiblePassage cible : cibles) {
            if (jeton.estAnnule()) {
                interrompu = true;
                issues.add(IssueTraitement.nonTraite(cible));
                continue;
            }
            issues.add(traiter(action, cible, jeton, journal));
        }
        return new ResultatTraitementGroupe(action.libelle(), issues, interrompu);
    }

    private IssueTraitement traiter(
            ActionGroupee action, CiblePassage cible, JetonAnnulation jeton, Consumer<String> journal) {
        var motifNonEligible = action.motifNonEligible(cible);
        if (motifNonEligible.isPresent()) {
            journal.accept(prefixe(cible) + "écarté : " + motifNonEligible.get());
            return IssueTraitement.ecarte(cible, motifNonEligible.get());
        }
        journal.accept(prefixe(cible) + action.libelle() + "…");
        try {
            action.executer(cible, jeton);
            journal.accept(prefixe(cible) + "terminé");
            return IssueTraitement.reussi(cible);
        } catch (RuntimeException echec) {
            String motif = redactionEchec.apply(echec);
            journal.accept(prefixe(cible) + "ÉCHEC : " + motif);
            return IssueTraitement.echec(cible, motif);
        }
    }

    /// Le fait, sans le geste. Le nom de la classe sert de dernier recours : une exception sans message
    /// laisserait une ligne vide, ce qui se lit comme « rien à signaler ».
    private static String faitSeul(RuntimeException echec) {
        return echec.getMessage() == null ? echec.getClass().getSimpleName() : echec.getMessage();
    }

    /// Préfixe de journal identifiant le passage (critère d'acceptation de #2357).
    private static String prefixe(CiblePassage cible) {
        return "[" + cible.designation() + "] ";
    }
}
