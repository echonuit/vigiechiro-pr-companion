package fr.univ_amu.iut.recette;

/// Le témoin que [SansEcritureHorsDuFilEteintTest] lance dans une JVM à part (#6142).
///
/// Il joue l'extension autour d'un cas vide, comme JUnit le ferait, et rend la main. Ce qu'il
/// prouve se lit de l'extérieur, dans la liste des classes que la JVM a chargées.
public final class EspionEteintTemoin {

    private EspionEteintTemoin() {}

    /// Joue `beforeEach` puis `afterEach`. Sans l'agent ByteBuddy sur la ligne de commande, la pose
    /// de la sonde refuse : le refus est attendu ici, le registre ayant déjà été chargé.
    public static void main(String[] arguments) {
        SansEcritureHorsDuFil extension = new SansEcritureHorsDuFil();
        try {
            extension.beforeEach(null);
            extension.afterEach(null);
        } catch (IllegalStateException sansAgent) {
            System.out.println("pose refusée faute d'agent : " + sansAgent.getMessage());
        }
    }
}
