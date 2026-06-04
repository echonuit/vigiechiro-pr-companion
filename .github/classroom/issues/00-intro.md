# 🦇 Bienvenue : comment travailler sur ce projet

Cette issue est votre **point de départ**. Lisez-la en entier avant de commencer.

## Le projet en une phrase

Vous construisez l'**interface graphique (JavaFX / MVVM)** de *VigieChiro PR Companion*, une appli
qui accompagne le traitement d'une nuit d'enregistrement de chauves-souris, de la carte SD jusqu'au
dépôt sur la plateforme Vigie-Chiro. Le **socle** (modèle, services, base de données, navigation) est
**déjà fourni et testé** : votre travail, c'est l'**IHM** de 8 *features*.

## Ce qui est fourni vs ce que vous construisez

- ✅ **Fourni (référence)** : la feature `sites` est **entièrement construite** — c'est votre
  modèle. L'accueil (le « chrome » de l'appli) aussi. Tout le `model` / `dao` / service des autres
  features également.
- 🚧 **À construire** : pour les 8 autres features, vous écrivez **3 choses** par écran :
  1. le **ViewModel** (`...ViewModel.java`) : on vous donne les propriétés observables et les
     signatures, vous écrivez le **corps** des méthodes ;
  2. la **vue FXML** (`....fxml`) : aujourd'hui un *placeholder* « à construire », vous écrivez la
     vraie mise en page ;
  3. le **controleur** (`...Controller.java`) : une coquille, vous reliez les `@FXML` au ViewModel.

## La méthode : TDD à petits pas, une issue = un fichier

Chaque feature est livrée avec un **test d'acceptation désactivé** (`@Disabled`). Le test est votre
**cahier des charges exécutable**.

> 🔴 Activez le test (retirez `@Disabled`) → voyez-le **rouge** → construisez le minimum pour le
> rendre **vert** → nettoyez. C'est tout le cycle.

Le travail est **découpé en issues, une par fichier** : chaque issue ne vous demande de modifier
**qu'un seul fichier**. Faites-les **dans l'ordre** (le numéro de l'issue donne l'ordre). Pour chaque
feature, une **issue chapeau** donne la vue d'ensemble de l'écran, puis les issues « fichier »
détaillent chaque étape.

## Le workflow (comme en entreprise)

Pour **chaque** issue :
1. Créez une **branche** : `git switch -c <type>/<feature>-<fichier>` (ex. `feat/diagnostic-viewmodel`).
2. Faites la modification **du seul fichier concerné**.
3. `./mvnw -Dglass.platform=Headless -Dprism.order=sw test` pour vérifier.
4. **Commit** (messages clairs, en français : `feat:`, `fix:`, `style:`…) puis **Pull Request**.
5. Faites **relire** la PR par un binôme (review), corrigez, **mergez**. Reliez la PR à l'issue
   (`Closes #N`).

> 💡 Aidez-vous de **Copilot Chat en mode tuteur** : demandez-lui d'**expliquer** le code de
> référence `sites`, jamais d'écrire la solution à votre place.

## Ordre conseillé des features

De la plus simple à la plus complète :
**diagnostic → lot → bibliotheque → importation → qualification → validation → multisite → passage**.

Les écrans `passage`, `qualification`, `lot`, `validation`, `diagnostic` s'atteignent depuis l'écran
pivot **M-Passage** : construire `passage` en dernier permet de **tout relier** (le fil rouge complet
import → vérification → dépôt → validation passera alors au vert).

## Definition of Done (s'applique à TOUTE issue)

- [ ] La modification ne touche **qu'un seul fichier** (celui de l'issue).
- [ ] `./mvnw -Dglass.platform=Headless -Dprism.order=sw test` ne **régresse pas** (aucun test qui
      passait avant ne casse).
- [ ] Le code **compile** et **Spotless** est content (`./mvnw spotless:check`).
- [ ] Le code respecte le **MVVM** : le ViewModel n'importe jamais `javafx.scene` ; la vue/le
      controleur ne contiennent **aucune logique métier** ni accès base (sinon les tests **ArchUnit**
      échouent).
- [ ] La modification est passée par une **branche + Pull Request relue**, et la PR référence l'issue.

## Critères d'acceptation de cette issue

- [ ] Vous avez lu cette page et la section « Votre travail » du **brief**.
- [ ] Vous avez ouvert et **lu** la feature de référence `sites` (`SiteDetailController`,
      `SiteDetail.fxml`, `SiteDetailViewModel`) : c'est le patron que vous réutiliserez partout.
- [ ] Vous avez lancé l'appli une première fois (`./mvnw javafx:run`) et reconnu l'accueil + l'écran
      « Mes sites » (déjà construits).

Quand c'est fait, fermez cette issue et attaquez la première feature.
