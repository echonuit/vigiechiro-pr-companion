package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.SuiviLignes;
import java.util.List;
import java.util.stream.IntStream;

/// Table de suivi des fichiers de l'import (#947) côté ViewModel : spécialise le socle [SuiviLignes]
/// pour traduire le cycle de vie reçu du [fr.univ_amu.iut.importation.model.SuiviFichiers] (plan établi
/// → copie → transformation → terminé / rejeté #155) en [LigneFichierImport] observables. Chaque
/// événement cible sa ligne par **numéro** (la transformation étant parallèle #12, ils arrivent dans le
/// désordre) ; un numéro inconnu est ignoré sans erreur.
///
/// La **fraction** d'une ligne matérialise ses étapes : 0 au départ, **0,5 une fois copiée** (mode
/// conservation), 1 une fois transformée. En mode « sans copie », aucun événement de copie n'arrive :
/// la barre reste à 0 pendant la transformation puis passe à 1.
///
/// **Fil JavaFX** : ces méthodes mutent des collections/propriétés observables ; l'appelant (le
/// controller) les invoque sur le fil JavaFX fourni par le socle (`ExecuteurTache#surFilJavaFx()`), comme le
/// callback de progression global.
public final class SuiviLignesFichiers extends SuiviLignes<LigneFichierImport> {

    private static final String ETAPE_COPIE = "Copie";
    private static final String ETAPE_TRANSFORMATION = "Transformation";

    /// Pré-remplit la table d'une ligne « en attente » par original du plan (l'ordre fixe les numéros
    /// 1..N). En multi-nuits, chaque nuit rappelle cette méthode : la table repart sur la nuit en cours.
    public void planifier(List<String> noms) {
        remplacerLignes(IntStream.range(0, noms.size())
                .mapToObj(i -> new LigneFichierImport(i + 1, noms.get(i)))
                .toList());
    }

    /// La copie protégée du fichier `numero` commence : ligne « en cours », étape « Copie ».
    public void copieDemarree(int numero) {
        ligne(numero).ifPresent(l -> {
            l.demarrer();
            l.poserEtape(ETAPE_COPIE);
        });
    }

    /// Le fichier `numero` est copié : sa barre marque la mi-parcours (la transformation suivra).
    public void copieTerminee(int numero) {
        ligne(numero).ifPresent(l -> l.progresser(0.5));
    }

    /// La transformation du fichier `numero` commence : étape « Transformation », sans reculer la barre
    /// (elle reste à 0,5 si le fichier a été copié, à 0 en mode sans copie).
    public void transformationDemarree(int numero) {
        ligne(numero).ifPresent(l -> {
            l.progresser(0.0);
            l.poserEtape(ETAPE_TRANSFORMATION);
        });
    }

    /// L'unité `numero` est traitée : sa ligne passe « terminée » et son étape s'efface.
    @Override
    public void terminer(int numero) {
        ligne(numero).ifPresent(l -> {
            l.terminer();
            l.poserEtape("");
        });
    }

    /// Le fichier `numero` est rejeté (#155) : sa ligne passe « échec » (raison en infobulle) et son
    /// étape s'efface.
    @Override
    public void echouer(int numero, String raison) {
        ligne(numero).ifPresent(l -> {
            l.echouer(raison);
            l.poserEtape("");
        });
    }
}
