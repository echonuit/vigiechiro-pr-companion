package fr.univ_amu.iut.analyse.viewmodel;

import fr.univ_amu.iut.analyse.model.LigneSynthese;
import fr.univ_amu.iut.analyse.model.ServiceSynthese;
import fr.univ_amu.iut.commun.model.ContexteActivite;
import fr.univ_amu.iut.commun.model.ReferentielActivite;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de la **synthèse d'une nuit** (#2351) : le tableau par espèce, et ce à quoi il compare.
///
/// Deux commandes seulement, et toutes deux **recalculent l'ensemble** : la bascule « identifications
/// validées seulement » et le choix du milieu. Rien n'est masqué : c'est un recalcul, parce que changer
/// l'un ou l'autre change les contacts retenus, donc la classe d'activité de chaque espèce. Deux
/// lectures cohabitent volontairement : ce que la machine propose, et ce que l'observateur a confirmé.
public class SyntheseViewModel {

    private final ServiceSynthese service;

    private final ObservableList<LigneSynthese> lignes = FXCollections.observableArrayList();
    private final BooleanProperty validesSeulement = new SimpleBooleanProperty(this, "validesSeulement", false);
    private final ObjectProperty<String> milieu = new SimpleObjectProperty<>(this, "milieu");

    /// Ce à quoi la comparaison a été faite, en clair : « milieu Foret · Été ». **Nommé à l'écran**,
    /// parce qu'une classe dont on ignore la référence est un oracle.
    private final ReadOnlyStringWrapper referentielEmploye = new ReadOnlyStringWrapper(this, "referentielEmploye", "");

    /// Le cadre de lecture du tableau : date, contacts, richesse.
    private final ReadOnlyStringWrapper contexteNuit = new ReadOnlyStringWrapper(this, "contexteNuit", "");

    /// Retour de la **dernière opération** avec sa sévérité, pour le bandeau mutualisé.
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

    /// Le contexte de comparaison **effectivement retenu** au dernier recalcul. Conservé plutôt que
    /// recalculé à la demande : l'export doit emporter exactement ce que l'écran affiche, et non ce
    /// qu'un second appel au service rendrait éventuellement.
    private ContexteActivite contexteActivite = ContexteActivite.NATIONAL;

    private long idPassage;
    private String numeroCarre;

    public SyntheseViewModel(ServiceSynthese service) {
        this.service = Objects.requireNonNull(service, "service");
        validesSeulement.addListener((observable, avant, apres) -> recalculer());
        milieu.addListener((observable, avant, apres) -> recalculer());
    }

    /// Charge la synthèse d'un passage. Les changements ultérieurs de bascule ou de milieu recalculent
    /// sans recharger la source : les observations ne bougent pas, seule la lecture change.
    public void charger(long idPassage, String numeroCarre) {
        this.idPassage = idPassage;
        this.numeroCarre = numeroCarre;
        recalculer();
    }

    private void recalculer() {
        List<LigneSynthese> calculees = service.pour(idPassage, validesSeulement.get(), numeroCarre, milieu.get());
        lignes.setAll(calculees);
        contexteActivite = service.contexte(idPassage, numeroCarre, milieu.get());
        referentielEmploye.set(contexteActivite.libelle());
        contexteNuit.set(resume(calculees));
    }

    /// « 6 espèces · 718 contacts · 4 chiroptères ». La richesse en chiroptères est comptée à part : le
    /// protocole ne vise qu'eux, et le total toutes catégories confondues gonflerait d'orthoptères.
    private static String resume(List<LigneSynthese> lignes) {
        if (lignes.isEmpty()) {
            return "Aucune espèce identifiée";
        }
        int contacts = lignes.stream().mapToInt(LigneSynthese::contacts).sum();
        long chiropteres = lignes.stream()
                .filter(ligne -> "Chiroptères".equals(ligne.groupe()))
                .count();
        return lignes.size() + " espèce(s) · " + contacts + " contact(s) · " + chiropteres + " chiroptère(s)";
    }

    public ObservableList<LigneSynthese> lignes() {
        return lignes;
    }

    public BooleanProperty validesSeulementProperty() {
        return validesSeulement;
    }

    public ObjectProperty<String> milieuProperty() {
        return milieu;
    }

    public javafx.beans.property.ReadOnlyStringProperty referentielEmployeProperty() {
        return referentielEmploye.getReadOnlyProperty();
    }

    public javafx.beans.property.ReadOnlyStringProperty contexteNuitProperty() {
        return contexteNuit.getReadOnlyProperty();
    }

    /// Les milieux offerts au choix, sans préfixe technique.
    public List<String> milieuxDisponibles() {
        return service.milieuxDisponibles();
    }

    /// Le référentiel est-il exploitable ? Faux, l'écran masque la colonne d'activité et le sélecteur
    /// plutôt que d'afficher des cellules vides ; le tableau de comptages reste entier.
    public boolean referentielDisponible() {
        return service.referentielDisponible();
    }

    /// La citation **obligatoire** de la source, à afficher en permanence et à recopier dans l'export.
    public String citation() {
        return ReferentielActivite.CITATION;
    }

    /// Ce que la classe d'activité ne dit pas. Permanent, jamais repliable : si l'avertissement ne
    /// voyage pas avec la donnée, il ne sert à rien.
    public String avertissement() {
        return ReferentielActivite.AVERTISSEMENT;
    }

    /// Les lignes à exporter : **exactement celles affichées**, bascule et milieu compris. L'export ne
    /// refait pas le calcul de son côté : un fichier qui ne correspondrait pas à l'écran d'où on l'a
    /// demandé serait pire qu'absent.
    public List<LigneSynthese> lignesExport() {
        return List.copyOf(lignes);
    }

    /// Le contexte de comparaison à recopier en tête du fichier.
    public ContexteActivite contexteActivite() {
        return contexteActivite;
    }

    /// Retour de la dernière opération, pour le bandeau mutualisé ([RetourOperation#AUCUN] en nominal).
    public ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }

    /// Signale un **export réussi**, en nommant le fichier et en comptant les lignes : sans cela, un
    /// export qui a marché est indiscernable d'un clic sans effet.
    public void signalerExport(String nomFichier, int lignesEcrites) {
        retour.set(RetourOperation.succes(lignesEcrites + " espèce(s) exportée(s) vers " + nomFichier + "."));
    }

    /// Signale un **échec d'export** (disque plein, dossier en lecture seule). Le dire est le point :
    /// une exception avalée par le fil JavaFX laisse l'utilisateur devant un bouton qui ne fait rien.
    public void signalerEchecExport(String motif) {
        retour.set(RetourOperation.erreur("L'export de la synthèse a échoué : " + motif));
    }

    /// Efface le retour (l'utilisateur a lu le bandeau et le ferme).
    public void effacerRetour() {
        retour.set(RetourOperation.AUCUN);
    }
}
