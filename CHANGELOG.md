# Journal des modifications

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/) et le versionnage [SemVer](https://semver.org/lang/fr/). Les entrées sont ajoutées automatiquement par semantic-release à chaque version.

# [2.190.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.189.0...v2.190.0) (2026-08-31)


### Bug Fixes

* **adr:** l'ADR des pictogrammes se remesure sur l'arbre qui la porte ([#4389](https://github.com/echonuit/vigiechiro-pr-companion/issues/4389)) ([4effcbb](https://github.com/echonuit/vigiechiro-pr-companion/commit/4effcbb06ac2a986d572a3e9aa5359b542ee0fc6)), closes [#4388](https://github.com/echonuit/vigiechiro-pr-companion/issues/4388) [#4334](https://github.com/echonuit/vigiechiro-pr-companion/issues/4334)
* **adr:** trois décisions portées se remesurent, et cessent de citer des gardes absents ([#4391](https://github.com/echonuit/vigiechiro-pr-companion/issues/4391)) ([9da19a6](https://github.com/echonuit/vigiechiro-pr-companion/commit/9da19a6f75aace0805573e1beb84b69ef14b4429)), closes [#4390](https://github.com/echonuit/vigiechiro-pr-companion/issues/4390) [#4334](https://github.com/echonuit/vigiechiro-pr-companion/issues/4334) [#4387](https://github.com/echonuit/vigiechiro-pr-companion/issues/4387) [#4389](https://github.com/echonuit/vigiechiro-pr-companion/issues/4389)
* **api:** l'écriture d'une participation cesse d'écraser celle d'un autre poste ([#4602](https://github.com/echonuit/vigiechiro-pr-companion/issues/4602)) ([2aa5bea](https://github.com/echonuit/vigiechiro-pr-companion/commit/2aa5bea3ddaf4315a19b34f04d5fab27dd91271c)), closes [#4523](https://github.com/echonuit/vigiechiro-pr-companion/issues/4523) [#4552](https://github.com/echonuit/vigiechiro-pr-companion/issues/4552)
* **api:** l'If-Match cesse de partir là où personne ne le lit ([#4551](https://github.com/echonuit/vigiechiro-pr-companion/issues/4551)) ([7cc786d](https://github.com/echonuit/vigiechiro-pr-companion/commit/7cc786df652195a997e095acab11391cdfbd0819)), closes [#4523](https://github.com/echonuit/vigiechiro-pr-companion/issues/4523) [#4444](https://github.com/echonuit/vigiechiro-pr-companion/issues/4444)
* **api:** la garde de concurrence compare contre la base, pas contre deux lectures voisines ([#4747](https://github.com/echonuit/vigiechiro-pr-companion/issues/4747)) ([e33162e](https://github.com/echonuit/vigiechiro-pr-companion/commit/e33162e7df1650322a098a66a692c13885da6632)), closes [#4707](https://github.com/echonuit/vigiechiro-pr-companion/issues/4707) [#4603](https://github.com/echonuit/vigiechiro-pr-companion/issues/4603) [#4707](https://github.com/echonuit/vigiechiro-pr-companion/issues/4707) [#4572](https://github.com/echonuit/vigiechiro-pr-companion/issues/4572)
* **api:** la garde de concurrence regarde les champs qu'on écrit, pas l'étiquette ([#4632](https://github.com/echonuit/vigiechiro-pr-companion/issues/4632)) ([71fdb70](https://github.com/echonuit/vigiechiro-pr-companion/commit/71fdb703ef491e1b12e00540050ad0ab51baff58)), closes [#4552](https://github.com/echonuit/vigiechiro-pr-companion/issues/4552) [#4603](https://github.com/echonuit/vigiechiro-pr-companion/issues/4603)
* **api:** un écart de date ne fait plus renoncer, les enregistrements prouvent la nôtre ([#4759](https://github.com/echonuit/vigiechiro-pr-companion/issues/4759)) ([b68948f](https://github.com/echonuit/vigiechiro-pr-companion/commit/b68948ff25a0b0dc24352655292c4ead3da88543)), closes [#4756](https://github.com/echonuit/vigiechiro-pr-companion/issues/4756)
* **api:** une coupure réseau dit qu'elle est une coupure, et cesse de passer pour une absence ([#4858](https://github.com/echonuit/vigiechiro-pr-companion/issues/4858)) ([09743be](https://github.com/echonuit/vigiechiro-pr-companion/commit/09743be6d2d088df74863c9bd214f5e0f13c1fad)), closes [#4631](https://github.com/echonuit/vigiechiro-pr-companion/issues/4631)
* **audit:** les commentaires de l’audit citaient un numéro qui n’est pas le leur ([#4284](https://github.com/echonuit/vigiechiro-pr-companion/issues/4284)) ([f653b1c](https://github.com/echonuit/vigiechiro-pr-companion/commit/f653b1c3785ff9a4c08f8552bd43ad6cff682b2c))
* **carte:** les quatre mailles sans numero sont tranchees, et le referentiel se garde ([#4681](https://github.com/echonuit/vigiechiro-pr-companion/issues/4681)) ([c627a7a](https://github.com/echonuit/vigiechiro-pr-companion/commit/c627a7a2d0f7c613e1be44f0544292235a3bb2a8))
* **ci:** deux des quatre rouges de la suite hebdomadaire, ceux qui s'éprouvent sous Linux ([#4539](https://github.com/echonuit/vigiechiro-pr-companion/issues/4539)) ([177aed9](https://github.com/echonuit/vigiechiro-pr-companion/commit/177aed9c6f744702e20b973685440f2a193080c5)), closes [#3645](https://github.com/echonuit/vigiechiro-pr-companion/issues/3645) [#3778](https://github.com/echonuit/vigiechiro-pr-companion/issues/3778) [#4522](https://github.com/echonuit/vigiechiro-pr-companion/issues/4522) [#4522](https://github.com/echonuit/vigiechiro-pr-companion/issues/4522) [#3645](https://github.com/echonuit/vigiechiro-pr-companion/issues/3645) [#3778](https://github.com/echonuit/vigiechiro-pr-companion/issues/3778)
* **ci:** la règle d'élision distingue l'article élidé du symbole ([#4484](https://github.com/echonuit/vigiechiro-pr-companion/issues/4484)) ([a0ee2e1](https://github.com/echonuit/vigiechiro-pr-companion/commit/a0ee2e1335a46c95596c7f6b4ff0d2f86ef4d2d2)), closes [#4483](https://github.com/echonuit/vigiechiro-pr-companion/issues/4483)
* **ci:** le checkout cesse de cloner tout l'historique, et l'historique se reprend ([#4482](https://github.com/echonuit/vigiechiro-pr-companion/issues/4482)) ([1a30269](https://github.com/echonuit/vigiechiro-pr-companion/commit/1a302690a4490022517c50804ac601730a4fe9cf)), closes [#4440](https://github.com/echonuit/vigiechiro-pr-companion/issues/4440) [#3525](https://github.com/echonuit/vigiechiro-pr-companion/issues/3525) [#2738](https://github.com/echonuit/vigiechiro-pr-companion/issues/2738)
* **ci:** le garde du stage attend la mise en page avant de la mesurer, et cesse d'accuser à tort ([#4540](https://github.com/echonuit/vigiechiro-pr-companion/issues/4540)) ([723590d](https://github.com/echonuit/vigiechiro-pr-companion/commit/723590da4970827f24b536c55c7de1408bfe62d6)), closes [#4504](https://github.com/echonuit/vigiechiro-pr-companion/issues/4504) [#4504](https://github.com/echonuit/vigiechiro-pr-companion/issues/4504)
* **ci:** le tournage éprouve son jeton au lieu de constater qu’il est là ([#4362](https://github.com/echonuit/vigiechiro-pr-companion/issues/4362)) ([0ff54a2](https://github.com/echonuit/vigiechiro-pr-companion/commit/0ff54a2a5b169a87ace763aa016ebeeb7fbbacae)), closes [#4305](https://github.com/echonuit/vigiechiro-pr-companion/issues/4305) [#4328](https://github.com/echonuit/vigiechiro-pr-companion/issues/4328) [#4291](https://github.com/echonuit/vigiechiro-pr-companion/issues/4291) [#4305](https://github.com/echonuit/vigiechiro-pr-companion/issues/4305) [#4328](https://github.com/echonuit/vigiechiro-pr-companion/issues/4328) [#4291](https://github.com/echonuit/vigiechiro-pr-companion/issues/4291)
* **ci:** un passage tronque ne se declare plus complet, et son juge a enfin un temoin ([#4555](https://github.com/echonuit/vigiechiro-pr-companion/issues/4555)) ([3a1f993](https://github.com/echonuit/vigiechiro-pr-companion/commit/3a1f9931b5d66908c3c50d01562c7c116de7443f)), closes [#4544](https://github.com/echonuit/vigiechiro-pr-companion/issues/4544) [#4544](https://github.com/echonuit/vigiechiro-pr-companion/issues/4544)
* **css:** retirer des marqueurs de conflit que j’ai poussés sur main ([#4310](https://github.com/echonuit/vigiechiro-pr-companion/issues/4310)) ([f011e41](https://github.com/echonuit/vigiechiro-pr-companion/commit/f011e41bdd39ec33bf46ff0f3b240c788ec0213f)), closes [#4294](https://github.com/echonuit/vigiechiro-pr-companion/issues/4294) [#4294](https://github.com/echonuit/vigiechiro-pr-companion/issues/4294) [#4294](https://github.com/echonuit/vigiechiro-pr-companion/issues/4294)
* **docs:** deux ADR portaient deux titres, et main en était rouge ([#4932](https://github.com/echonuit/vigiechiro-pr-companion/issues/4932)) ([5a5de3d](https://github.com/echonuit/vigiechiro-pr-companion/commit/5a5de3d2fd7a81a5a17a1791e3ecb6a15bae589d)), closes [DocumentationAJourTest#l_entete_d_une_adr_porte_son_titre](https://github.com/DocumentationAJourTest/issues/l_entete_d_une_adr_porte_son_titre) [#4882](https://github.com/echonuit/vigiechiro-pr-companion/issues/4882)
* **docs:** la valeur d'un cliquet se porte en balise, elle ne se recopie plus ([#4403](https://github.com/echonuit/vigiechiro-pr-companion/issues/4403)) ([8665c76](https://github.com/echonuit/vigiechiro-pr-companion/commit/8665c76f5c9d4ebe6ed3574c052f687b937a7586)), closes [#4387](https://github.com/echonuit/vigiechiro-pr-companion/issues/4387) [#4391](https://github.com/echonuit/vigiechiro-pr-companion/issues/4391) [#2385](https://github.com/echonuit/vigiechiro-pr-companion/issues/2385) [#4392](https://github.com/echonuit/vigiechiro-pr-companion/issues/4392) [#3466](https://github.com/echonuit/vigiechiro-pr-companion/issues/3466) [#4334](https://github.com/echonuit/vigiechiro-pr-companion/issues/4334)
* **garde:** la ligne de spécification se reconnaît entière, pas au seul mot OpenSpec ([#4947](https://github.com/echonuit/vigiechiro-pr-companion/issues/4947)) ([1c75589](https://github.com/echonuit/vigiechiro-pr-companion/commit/1c75589fd25e764d9f6b6c81d38ee7440c66b7e4)), closes [#4882](https://github.com/echonuit/vigiechiro-pr-companion/issues/4882) [#4882](https://github.com/echonuit/vigiechiro-pr-companion/issues/4882)
* **garde:** la loupe de l'ADR 4712 rend son verdict, et le rapport la montre ([#4774](https://github.com/echonuit/vigiechiro-pr-companion/issues/4774)) ([2e9a672](https://github.com/echonuit/vigiechiro-pr-companion/commit/2e9a6729903f5d71d4ff11384d65a0a53ab28783)), closes [#4758](https://github.com/echonuit/vigiechiro-pr-companion/issues/4758)
* **garde:** le cliquet des pictogrammes ne voyait pas « ⚠️ suivi de gras » ([#4480](https://github.com/echonuit/vigiechiro-pr-companion/issues/4480)) ([36a8d2f](https://github.com/echonuit/vigiechiro-pr-companion/commit/36a8d2fcdd5653bbf28c693abdc446f4f95cb312)), closes [#4464](https://github.com/echonuit/vigiechiro-pr-companion/issues/4464) [#4366](https://github.com/echonuit/vigiechiro-pr-companion/issues/4366)
* **garde:** le cliquet du portail suit un test qui couvre un cas de plus ([#4641](https://github.com/echonuit/vigiechiro-pr-companion/issues/4641)) ([3271b83](https://github.com/echonuit/vigiechiro-pr-companion/commit/3271b83f8cf1dc14e082b036d0ef95355dc9fe4b)), closes [#4633](https://github.com/echonuit/vigiechiro-pr-companion/issues/4633) [#4633](https://github.com/echonuit/vigiechiro-pr-companion/issues/4633) [#4632](https://github.com/echonuit/vigiechiro-pr-companion/issues/4632) [#4617](https://github.com/echonuit/vigiechiro-pr-companion/issues/4617) [#4632](https://github.com/echonuit/vigiechiro-pr-companion/issues/4632)
* **garde:** le garde des captures ne juge plus à travers un tube ([#4687](https://github.com/echonuit/vigiechiro-pr-companion/issues/4687)) ([f8bea79](https://github.com/echonuit/vigiechiro-pr-companion/commit/f8bea7920eb437441202261f607813cf55eebfd1)), closes [#4641](https://github.com/echonuit/vigiechiro-pr-companion/issues/4641) [#4642](https://github.com/echonuit/vigiechiro-pr-companion/issues/4642) [#4685](https://github.com/echonuit/vigiechiro-pr-companion/issues/4685)
* **garde:** le garde des témoins mute un arbre jetable, plus le dépôt ([#4784](https://github.com/echonuit/vigiechiro-pr-companion/issues/4784)) ([4d4bb47](https://github.com/echonuit/vigiechiro-pr-companion/commit/4d4bb47f751da81e15fcef0f17eaa703afc6668a)), closes [#4686](https://github.com/echonuit/vigiechiro-pr-companion/issues/4686) [#4686](https://github.com/echonuit/vigiechiro-pr-companion/issues/4686) [#4700](https://github.com/echonuit/vigiechiro-pr-companion/issues/4700) [#4700](https://github.com/echonuit/vigiechiro-pr-companion/issues/4700)
* **garde:** le manifeste de relecture refuse ce qu'il ne saurait garder, et nomme ce qu'il a perdu ([#4530](https://github.com/echonuit/vigiechiro-pr-companion/issues/4530)) ([40d2b14](https://github.com/echonuit/vigiechiro-pr-companion/commit/40d2b148142919fc83ada5390ace884bb0c01db3)), closes [#4525](https://github.com/echonuit/vigiechiro-pr-companion/issues/4525) [#4414](https://github.com/echonuit/vigiechiro-pr-companion/issues/4414) [#4527](https://github.com/echonuit/vigiechiro-pr-companion/issues/4527)
* **garde:** le plancher compte les issues citées, non leurs occurrences ([#4399](https://github.com/echonuit/vigiechiro-pr-companion/issues/4399)) ([fe56a3f](https://github.com/echonuit/vigiechiro-pr-companion/commit/fe56a3fddfb8053e951e058d275bdefb58b54a33)), closes [#3068](https://github.com/echonuit/vigiechiro-pr-companion/issues/3068) [#4398](https://github.com/echonuit/vigiechiro-pr-companion/issues/4398) [#4395](https://github.com/echonuit/vigiechiro-pr-companion/issues/4395) [#4394](https://github.com/echonuit/vigiechiro-pr-companion/issues/4394)
* **garde:** le plancher des renvois verrouille les vingt-quatre gagnes, et le resserreur remarche ([#4669](https://github.com/echonuit/vigiechiro-pr-companion/issues/4669)) ([afc52d5](https://github.com/echonuit/vigiechiro-pr-companion/commit/afc52d5c2bf36e59973f81560a10817a9d631544)), closes [#4647](https://github.com/echonuit/vigiechiro-pr-companion/issues/4647) [#4647](https://github.com/echonuit/vigiechiro-pr-companion/issues/4647) [#4646](https://github.com/echonuit/vigiechiro-pr-companion/issues/4646) [#4635](https://github.com/echonuit/vigiechiro-pr-companion/issues/4635)
* **garde:** le portail compte chaque zone à part, et la production est à zéro ([#4689](https://github.com/echonuit/vigiechiro-pr-companion/issues/4689)) ([83f78f7](https://github.com/echonuit/vigiechiro-pr-companion/commit/83f78f751bb1bc0d01452ac61f3cd1e314614da9)), closes [#4682](https://github.com/echonuit/vigiechiro-pr-companion/issues/4682)
* **garde:** le rapport dit ce qu'il n'a pas su lire, et la loupe rend son numero ([#4647](https://github.com/echonuit/vigiechiro-pr-companion/issues/4647)) ([6bd47cd](https://github.com/echonuit/vigiechiro-pr-companion/commit/6bd47cd5c65abbd4e6bb0753fbff66e686f08c5f)), closes [#4646](https://github.com/echonuit/vigiechiro-pr-companion/issues/4646) [#4635](https://github.com/echonuit/vigiechiro-pr-companion/issues/4635) [#4634](https://github.com/echonuit/vigiechiro-pr-companion/issues/4634)
* **garde:** les auto-tests des gardes de méthode se prouvent par mutation ([#4791](https://github.com/echonuit/vigiechiro-pr-companion/issues/4791)) ([e589e4f](https://github.com/echonuit/vigiechiro-pr-companion/commit/e589e4f58bf22b6d04d971e30c592197035994cb)), closes [#4788](https://github.com/echonuit/vigiechiro-pr-companion/issues/4788) [#4700](https://github.com/echonuit/vigiechiro-pr-companion/issues/4700) [#4760](https://github.com/echonuit/vigiechiro-pr-companion/issues/4760)
* **garde:** six gardes de méthode reçoivent un point d'entrée, et l'absence de preuve cesse de passer en vert ([#4832](https://github.com/echonuit/vigiechiro-pr-companion/issues/4832)) ([2a352fe](https://github.com/echonuit/vigiechiro-pr-companion/commit/2a352fe89c7cc1e250a57e6667da95b55b864f60)), closes [#4788](https://github.com/echonuit/vigiechiro-pr-companion/issues/4788)
* **gardes:** les deux cliquets de clôture vérifient leur prémisse, chacun où elle vit ([#4965](https://github.com/echonuit/vigiechiro-pr-companion/issues/4965)) ([dec895e](https://github.com/echonuit/vigiechiro-pr-companion/commit/dec895e7bb415a4052b44bf5ce6548de6e21f704))
* **garde:** trois gardes shell rendaient un verdict qui dépendait de la locale ([#4471](https://github.com/echonuit/vigiechiro-pr-companion/issues/4471)) ([43fc895](https://github.com/echonuit/vigiechiro-pr-companion/commit/43fc895cdaa23d515f6bccef44dafe48d57fd0f0)), closes [#4456](https://github.com/echonuit/vigiechiro-pr-companion/issues/4456) [#2947](https://github.com/echonuit/vigiechiro-pr-companion/issues/2947)
* **garde:** un garde d'ADR mesure son dépôt, et le corpus des décisions cesse de se recopier ([#4822](https://github.com/echonuit/vigiechiro-pr-companion/issues/4822)) ([bdced96](https://github.com/echonuit/vigiechiro-pr-companion/commit/bdced96869bc89b488547314b0fe341f76203207)), closes [#4781](https://github.com/echonuit/vigiechiro-pr-companion/issues/4781)
* **garde:** un plancher périmé refuse, et les six emplacements se relèvent d'un geste ([#4693](https://github.com/echonuit/vigiechiro-pr-companion/issues/4693)) ([c047057](https://github.com/echonuit/vigiechiro-pr-companion/commit/c047057ac6ed3ad609c915e4520280a19e060074)), closes [#4441](https://github.com/echonuit/vigiechiro-pr-companion/issues/4441) [#4646](https://github.com/echonuit/vigiechiro-pr-companion/issues/4646) [#4672](https://github.com/echonuit/vigiechiro-pr-companion/issues/4672) [#4646](https://github.com/echonuit/vigiechiro-pr-companion/issues/4646) [#4669](https://github.com/echonuit/vigiechiro-pr-companion/issues/4669) [#4683](https://github.com/echonuit/vigiechiro-pr-companion/issues/4683)
* **garde:** une sortie d'outil citée n'est plus une élision, et une élision cachée sous un gras en redevient une ([#4827](https://github.com/echonuit/vigiechiro-pr-companion/issues/4827)) ([4cf5305](https://github.com/echonuit/vigiechiro-pr-companion/commit/4cf5305295ef8c7e270a7e80e388f33b9eb0bc8f)), closes [#4483](https://github.com/echonuit/vigiechiro-pr-companion/issues/4483) [#4786](https://github.com/echonuit/vigiechiro-pr-companion/issues/4786)
* **javadoc:** les cinquante-trois renvois jamais remplis retrouvent leur numéro ([#4479](https://github.com/echonuit/vigiechiro-pr-companion/issues/4479)) ([7a65ca5](https://github.com/echonuit/vigiechiro-pr-companion/commit/7a65ca545114b42674068b0cdc25465d08e155a1)), closes [#4441](https://github.com/echonuit/vigiechiro-pr-companion/issues/4441) [#166](https://github.com/echonuit/vigiechiro-pr-companion/issues/166) [#4144](https://github.com/echonuit/vigiechiro-pr-companion/issues/4144) [#1118](https://github.com/echonuit/vigiechiro-pr-companion/issues/1118) [#4395](https://github.com/echonuit/vigiechiro-pr-companion/issues/4395) [#4334](https://github.com/echonuit/vigiechiro-pr-companion/issues/4334)
* **javadoc:** trois blocs cessent d'annoncer ce que le code ne fait pas ([#4436](https://github.com/echonuit/vigiechiro-pr-companion/issues/4436)) ([466b9c6](https://github.com/echonuit/vigiechiro-pr-companion/commit/466b9c6d47d6e50cd672284680744daf96c90c57)), closes [#4424](https://github.com/echonuit/vigiechiro-pr-companion/issues/4424) [#4425](https://github.com/echonuit/vigiechiro-pr-companion/issues/4425) [#4428](https://github.com/echonuit/vigiechiro-pr-companion/issues/4428) [#4430](https://github.com/echonuit/vigiechiro-pr-companion/issues/4430) [#4425](https://github.com/echonuit/vigiechiro-pr-companion/issues/4425) [#4428](https://github.com/echonuit/vigiechiro-pr-companion/issues/4428) [#4430](https://github.com/echonuit/vigiechiro-pr-companion/issues/4430) [#4394](https://github.com/echonuit/vigiechiro-pr-companion/issues/4394) [#4415](https://github.com/echonuit/vigiechiro-pr-companion/issues/4415)
* **libelles:** douze apostrophes courbes atteignaient l'écran, et rien ne les tenait ([#4371](https://github.com/echonuit/vigiechiro-pr-companion/issues/4371)) ([8b2bdbc](https://github.com/echonuit/vigiechiro-pr-companion/commit/8b2bdbc8aad5633885d71f4ba9402a78c322ce9c)), closes [#4343](https://github.com/echonuit/vigiechiro-pr-companion/issues/4343) [#4368](https://github.com/echonuit/vigiechiro-pr-companion/issues/4368)
* **methode:** la chaîne des six compétences suit l'ordre du cycle, et le titre n'a plus qu'un foyer ([#4722](https://github.com/echonuit/vigiechiro-pr-companion/issues/4722)) ([e9a9bef](https://github.com/echonuit/vigiechiro-pr-companion/commit/e9a9befd5adffe7c2f7d6649d1e9eab8f9a9834e)), closes [#4721](https://github.com/echonuit/vigiechiro-pr-companion/issues/4721) [#4721](https://github.com/echonuit/vigiechiro-pr-companion/issues/4721)
* **methode:** la documentation EST jugée par build, et la page disait le contraire ([#4945](https://github.com/echonuit/vigiechiro-pr-companion/issues/4945)) ([1392df1](https://github.com/echonuit/vigiechiro-pr-companion/commit/1392df1abf10e91b5c9189e8bd0aad562783e3e4)), closes [#4923](https://github.com/echonuit/vigiechiro-pr-companion/issues/4923)
* **methode:** la matrice de la constitution reprend sa valeur ([#4569](https://github.com/echonuit/vigiechiro-pr-companion/issues/4569)) ([e04fdfa](https://github.com/echonuit/vigiechiro-pr-companion/commit/e04fdfaa499ddfaf25af5e13194202702dc097c0)), closes [#4560](https://github.com/echonuit/vigiechiro-pr-companion/issues/4560) [#4568](https://github.com/echonuit/vigiechiro-pr-companion/issues/4568)
* **methode:** le générateur d'adaptateurs regarde dans les deux sens ([#4599](https://github.com/echonuit/vigiechiro-pr-companion/issues/4599)) ([64179ac](https://github.com/echonuit/vigiechiro-pr-companion/commit/64179ac2ac043c06c6ed9c9ae0bade29cb8fa43e)), closes [#4565](https://github.com/echonuit/vigiechiro-pr-companion/issues/4565) [#4566](https://github.com/echonuit/vigiechiro-pr-companion/issues/4566) [#4593](https://github.com/echonuit/vigiechiro-pr-companion/issues/4593)
* **methode:** le titre de PR se vérifie avant l'ouverture, là où la main le tape ([#4604](https://github.com/echonuit/vigiechiro-pr-companion/issues/4604)) ([55e2cb9](https://github.com/echonuit/vigiechiro-pr-companion/commit/55e2cb9a56b220adb7bfb2e0a71b73f8c5d2ae3e))
* **methode:** trois défauts que [#4454](https://github.com/echonuit/vigiechiro-pr-companion/issues/4454) a laissés sur main ([#4457](https://github.com/echonuit/vigiechiro-pr-companion/issues/4457)) ([b5ad71c](https://github.com/echonuit/vigiechiro-pr-companion/commit/b5ad71c2fc1f7223ca5f1a21e602fc85bc880eb3)), closes [#4453](https://github.com/echonuit/vigiechiro-pr-companion/issues/4453)
* **methode:** un corpus qui rétrécit rougit, au lieu de rester vert ([#4584](https://github.com/echonuit/vigiechiro-pr-companion/issues/4584)) ([930853f](https://github.com/echonuit/vigiechiro-pr-companion/commit/930853f41d8f42b9bd4f6ecb3fdb358d4d0d950d)), closes [#4516](https://github.com/echonuit/vigiechiro-pr-companion/issues/4516) [#4566](https://github.com/echonuit/vigiechiro-pr-companion/issues/4566)
* **methode:** un renvoi cité par une compétence a une cible ou un repli ([#4590](https://github.com/echonuit/vigiechiro-pr-companion/issues/4590)) ([1e33769](https://github.com/echonuit/vigiechiro-pr-companion/commit/1e33769987345d214ce0c1bd452b15c9851b3ffd)), closes [#4515](https://github.com/echonuit/vigiechiro-pr-companion/issues/4515) [#4514](https://github.com/echonuit/vigiechiro-pr-companion/issues/4514) [#4566](https://github.com/echonuit/vigiechiro-pr-companion/issues/4566) [#4564](https://github.com/echonuit/vigiechiro-pr-companion/issues/4564)
* **methode:** un test cité par une page existe, et un garde le tient ([#4746](https://github.com/echonuit/vigiechiro-pr-companion/issues/4746)) ([e2f0299](https://github.com/echonuit/vigiechiro-pr-companion/commit/e2f0299ef25cca43888ada12a609f590ba09dac2)), closes [SitesViewModelTest#chargeLesSites](https://github.com/SitesViewModelTest/issues/chargeLesSites) [#4650](https://github.com/echonuit/vigiechiro-pr-companion/issues/4650) [#4713](https://github.com/echonuit/vigiechiro-pr-companion/issues/4713) [#4745](https://github.com/echonuit/vigiechiro-pr-companion/issues/4745)
* **methode:** une étape du cycle ne délègue plus vers l'aval, et un garde le tient ([#4732](https://github.com/echonuit/vigiechiro-pr-companion/issues/4732)) ([5206a06](https://github.com/echonuit/vigiechiro-pr-companion/commit/5206a0697748a7e8eb87bf2be7adfa632612e868)), closes [#4722](https://github.com/echonuit/vigiechiro-pr-companion/issues/4722) [#4731](https://github.com/echonuit/vigiechiro-pr-companion/issues/4731)
* **outillage:** mesurer n'est pas résorber, et les balises suivent la mesure ([#4481](https://github.com/echonuit/vigiechiro-pr-companion/issues/4481)) ([44fa245](https://github.com/echonuit/vigiechiro-pr-companion/commit/44fa245ed80dba77d9a41eaf29c2a024e2a09d5a)), closes [#4469](https://github.com/echonuit/vigiechiro-pr-companion/issues/4469) [#4407](https://github.com/echonuit/vigiechiro-pr-companion/issues/4407) [#4407](https://github.com/echonuit/vigiechiro-pr-companion/issues/4407)
* **outillage:** resserrer un cliquet écrit aussi ses balises, et dit combien ([#4413](https://github.com/echonuit/vigiechiro-pr-companion/issues/4413)) ([0bd9c9d](https://github.com/echonuit/vigiechiro-pr-companion/commit/0bd9c9db03562dff0f90b2039d483e0ea4640143)), closes [#4403](https://github.com/echonuit/vigiechiro-pr-companion/issues/4403) [#4407](https://github.com/echonuit/vigiechiro-pr-companion/issues/4407) [#4403](https://github.com/echonuit/vigiechiro-pr-companion/issues/4403) [#4392](https://github.com/echonuit/vigiechiro-pr-companion/issues/4392)
* **passage:** le relevé garde les températures, et les envois repartent ([#4773](https://github.com/echonuit/vigiechiro-pr-companion/issues/4773)) ([e8b6f84](https://github.com/echonuit/vigiechiro-pr-companion/commit/e8b6f8445d90915237a30af1c5ef01e502184dcc)), closes [#4707](https://github.com/echonuit/vigiechiro-pr-companion/issues/4707) [#4768](https://github.com/echonuit/vigiechiro-pr-companion/issues/4768)
* **perf:** le banc semait un seul carré, donc il ne pouvait pas voir ce qu’il mesurait ([#4294](https://github.com/echonuit/vigiechiro-pr-companion/issues/4294)) ([6f9c712](https://github.com/echonuit/vigiechiro-pr-companion/commit/6f9c712ce1dd226fc137e40d1f8ed7bd4937a7aa))
* **qualification:** la modale de sélection édite celle de l'écran, et six cas la filment ([#4752](https://github.com/echonuit/vigiechiro-pr-companion/issues/4752)) ([c4d2ac5](https://github.com/echonuit/vigiechiro-pr-companion/commit/c4d2ac56cdc8de8c273d198e65bec244097d6e38)), closes [#1462](https://github.com/echonuit/vigiechiro-pr-companion/issues/1462) [#4734](https://github.com/echonuit/vigiechiro-pr-companion/issues/4734)
* **qualification:** la sélection dit ce qu'elle n'a pas pu lire, au lieu de s'amputer en silence ([#4855](https://github.com/echonuit/vigiechiro-pr-companion/issues/4855)) ([145111f](https://github.com/echonuit/vigiechiro-pr-companion/commit/145111f1d48db6a60faa15b36405714e775a07d8)), closes [#4739](https://github.com/echonuit/vigiechiro-pr-companion/issues/4739)
* **recette:** le banc lie aussi son client, pas seulement sa source de jeton ([#4354](https://github.com/echonuit/vigiechiro-pr-companion/issues/4354)) ([e950200](https://github.com/echonuit/vigiechiro-pr-companion/commit/e950200a484b2093313b947ba1ef90e7a2efd355)), closes [#4304](https://github.com/echonuit/vigiechiro-pr-companion/issues/4304) [#4332](https://github.com/echonuit/vigiechiro-pr-companion/issues/4332) [#4291](https://github.com/echonuit/vigiechiro-pr-companion/issues/4291)
* **recette:** le défilement se rejoue jusqu'à ce que la cible soit dans le cadre ([#4771](https://github.com/echonuit/vigiechiro-pr-companion/issues/4771)) ([0150cf1](https://github.com/echonuit/vigiechiro-pr-companion/commit/0150cf185943e7aae623f31f942b1e97f824e81b)), closes [#4704](https://github.com/echonuit/vigiechiro-pr-companion/issues/4704) [#4704](https://github.com/echonuit/vigiechiro-pr-companion/issues/4704) [#4723](https://github.com/echonuit/vigiechiro-pr-companion/issues/4723)
* **recette:** le geste fait défiler les panneaux dont la cible descend, pas le premier venu ([#4782](https://github.com/echonuit/vigiechiro-pr-companion/issues/4782)) ([b3ce923](https://github.com/echonuit/vigiechiro-pr-companion/commit/b3ce923056bcc3c36f121b68205f5b7be191b1c4)), closes [#4723](https://github.com/echonuit/vigiechiro-pr-companion/issues/4723) [#4447](https://github.com/echonuit/vigiechiro-pr-companion/issues/4447) [#4778](https://github.com/echonuit/vigiechiro-pr-companion/issues/4778)
* **recette:** le libellé d'un carton suit la grammaire partagée ([#4542](https://github.com/echonuit/vigiechiro-pr-companion/issues/4542)) ([98bf3a9](https://github.com/echonuit/vigiechiro-pr-companion/commit/98bf3a9631ac23cd821078105aea3868a90424bd)), closes [#4465](https://github.com/echonuit/vigiechiro-pr-companion/issues/4465) [#4465](https://github.com/echonuit/vigiechiro-pr-companion/issues/4465)
* **recette:** les deux clips connectés montrent enfin leur cas ([#4346](https://github.com/echonuit/vigiechiro-pr-companion/issues/4346)) ([16b8f48](https://github.com/echonuit/vigiechiro-pr-companion/commit/16b8f482368964800051d020938be1ad8feb3bd0)), closes [#4324](https://github.com/echonuit/vigiechiro-pr-companion/issues/4324) [#4291](https://github.com/echonuit/vigiechiro-pr-companion/issues/4291) [#4345](https://github.com/echonuit/vigiechiro-pr-companion/issues/4345) [#4345](https://github.com/echonuit/vigiechiro-pr-companion/issues/4345) [#4324](https://github.com/echonuit/vigiechiro-pr-companion/issues/4324) [#1376](https://github.com/echonuit/vigiechiro-pr-companion/issues/1376) [#4099](https://github.com/echonuit/vigiechiro-pr-companion/issues/4099)
* **recette:** les deux derniers rouges de la suite hebdomadaire, sous Windows et macOS ([#4528](https://github.com/echonuit/vigiechiro-pr-companion/issues/4528)) ([046765f](https://github.com/echonuit/vigiechiro-pr-companion/commit/046765f9c9124a8c3805085c18e18b316a46ec99)), closes [#4522](https://github.com/echonuit/vigiechiro-pr-companion/issues/4522) [#4260](https://github.com/echonuit/vigiechiro-pr-companion/issues/4260) [#4260](https://github.com/echonuit/vigiechiro-pr-companion/issues/4260)
* **recette:** les lectures du graphe se font sur le fil FX, pas dans l'attente ([#4796](https://github.com/echonuit/vigiechiro-pr-companion/issues/4796)) ([2e8c985](https://github.com/echonuit/vigiechiro-pr-companion/commit/2e8c985f4d41b1b13a20c2c11ba6454aa01768d8)), closes [#4771](https://github.com/echonuit/vigiechiro-pr-companion/issues/4771) [#4782](https://github.com/echonuit/vigiechiro-pr-companion/issues/4782) [#4795](https://github.com/echonuit/vigiechiro-pr-companion/issues/4795) [#4771](https://github.com/echonuit/vigiechiro-pr-companion/issues/4771) [#4782](https://github.com/echonuit/vigiechiro-pr-companion/issues/4782) [#4771](https://github.com/echonuit/vigiechiro-pr-companion/issues/4771) [#4782](https://github.com/echonuit/vigiechiro-pr-companion/issues/4782) [#4795](https://github.com/echonuit/vigiechiro-pr-companion/issues/4795)
* **recette:** un outil absent est une panne, pas cinquante mesures impossibles ([#4282](https://github.com/echonuit/vigiechiro-pr-companion/issues/4282)) ([c519fa6](https://github.com/echonuit/vigiechiro-pr-companion/commit/c519fa6e3ee8c467ceff879deadce35e2f942746)), closes [#4274](https://github.com/echonuit/vigiechiro-pr-companion/issues/4274)
* **recette:** un parcours E2E attend que l'écran soit chargé, il n'attend pas d'avoir raison ([#4534](https://github.com/echonuit/vigiechiro-pr-companion/issues/4534)) ([6525077](https://github.com/echonuit/vigiechiro-pr-companion/commit/65250779c2a5e1e2f366d48d53bc09effad8e40e)), closes [#4501](https://github.com/echonuit/vigiechiro-pr-companion/issues/4501)
* **securite:** l'appelant cesse d'hériter de tout le trousseau ([#4382](https://github.com/echonuit/vigiechiro-pr-companion/issues/4382)) ([1ec3beb](https://github.com/echonuit/vigiechiro-pr-companion/commit/1ec3bebdee1dffcf9bb18722bd0af5e7fb944b26)), closes [#2739](https://github.com/echonuit/vigiechiro-pr-companion/issues/2739) [#4291](https://github.com/echonuit/vigiechiro-pr-companion/issues/4291) [#4304](https://github.com/echonuit/vigiechiro-pr-companion/issues/4304) [#4349](https://github.com/echonuit/vigiechiro-pr-companion/issues/4349) [#4291](https://github.com/echonuit/vigiechiro-pr-companion/issues/4291) [#4304](https://github.com/echonuit/vigiechiro-pr-companion/issues/4304) [#2739](https://github.com/echonuit/vigiechiro-pr-companion/issues/2739)
* **securite:** les onze alertes CodeQL ouvertes sur main se ferment ([#4674](https://github.com/echonuit/vigiechiro-pr-companion/issues/4674)) ([89f1982](https://github.com/echonuit/vigiechiro-pr-companion/commit/89f1982dae5c4ec653905704aae0923f4b3141ec)), closes [#4510](https://github.com/echonuit/vigiechiro-pr-companion/issues/4510) [#4510](https://github.com/echonuit/vigiechiro-pr-companion/issues/4510) [#4510](https://github.com/echonuit/vigiechiro-pr-companion/issues/4510) [#4509](https://github.com/echonuit/vigiechiro-pr-companion/issues/4509) [#4510](https://github.com/echonuit/vigiechiro-pr-companion/issues/4510) [#4502](https://github.com/echonuit/vigiechiro-pr-companion/issues/4502) [#4617](https://github.com/echonuit/vigiechiro-pr-companion/issues/4617)
* **securite:** les trois alertes CodeQL ouvertes sur main se ferment ([#4510](https://github.com/echonuit/vigiechiro-pr-companion/issues/4510)) ([095bc14](https://github.com/echonuit/vigiechiro-pr-companion/commit/095bc14bebf103f1493526da62cb563be5df2051)), closes [#4509](https://github.com/echonuit/vigiechiro-pr-companion/issues/4509)
* **sites:** le controle du carre cesse de pouvoir crier a tort dans neuf departements ([#4679](https://github.com/echonuit/vigiechiro-pr-companion/issues/4679)) ([7d408b9](https://github.com/echonuit/vigiechiro-pr-companion/commit/7d408b9358f19645b71c556d546aeb9dc4a27849)), closes [#4573](https://github.com/echonuit/vigiechiro-pr-companion/issues/4573)
* **sites:** sur une frontière, le contrôle du carré cesse d'accuser un carré correctement déclaré ([#4680](https://github.com/echonuit/vigiechiro-pr-companion/issues/4680)) ([fd7b6bf](https://github.com/echonuit/vigiechiro-pr-companion/commit/fd7b6bf8cfdc44f61457bdafc936f17146f9b1db))
* **test:** dix bancs de plus referment, dont trois hors du cycle de test ([#4889](https://github.com/echonuit/vigiechiro-pr-companion/issues/4889)) ([2af8415](https://github.com/echonuit/vigiechiro-pr-companion/commit/2af84156de556cbaf667d83cca1489a41771b90f)), closes [#4868](https://github.com/echonuit/vigiechiro-pr-companion/issues/4868) [#4876](https://github.com/echonuit/vigiechiro-pr-companion/issues/4876) [#4859](https://github.com/echonuit/vigiechiro-pr-companion/issues/4859) [#4880](https://github.com/echonuit/vigiechiro-pr-companion/issues/4880)
* **test:** dix bancs de plus referment, et un fichier en cachait trois ([#4880](https://github.com/echonuit/vigiechiro-pr-companion/issues/4880)) ([d506cd2](https://github.com/echonuit/vigiechiro-pr-companion/commit/d506cd2adbe35f7012515ba4895ab4051a068f99)), closes [#4868](https://github.com/echonuit/vigiechiro-pr-companion/issues/4868) [#4859](https://github.com/echonuit/vigiechiro-pr-companion/issues/4859)
* **test:** dix bancs de plus referment, tous d'une seule forme ([#4906](https://github.com/echonuit/vigiechiro-pr-companion/issues/4906)) ([d786241](https://github.com/echonuit/vigiechiro-pr-companion/commit/d786241944a92657606cbe7009bd489e4c771e74)), closes [#4868](https://github.com/echonuit/vigiechiro-pr-companion/issues/4868) [#4859](https://github.com/echonuit/vigiechiro-pr-companion/issues/4859)
* **test:** dix bancs referment ce qu'ils ouvrent, et le garde le dit ([#4870](https://github.com/echonuit/vigiechiro-pr-companion/issues/4870)) ([860b9a3](https://github.com/echonuit/vigiechiro-pr-companion/commit/860b9a3ee4f4a47c6e0f16189606edbe6ffa4eff)), closes [#4859](https://github.com/echonuit/vigiechiro-pr-companion/issues/4859) [#4868](https://github.com/echonuit/vigiechiro-pr-companion/issues/4868)
* **test:** douze bancs de plus referment, après une détection que j'ai dû refaire ([#4916](https://github.com/echonuit/vigiechiro-pr-companion/issues/4916)) ([cb55b9e](https://github.com/echonuit/vigiechiro-pr-companion/commit/cb55b9eeb9fd0cfd7c7c8c4ad479ffcdeeee0501)), closes [#4859](https://github.com/echonuit/vigiechiro-pr-companion/issues/4859)
* **test:** l'accueil s'attend sur un prédicat, il ne s'asserte pas au retour du clic ([#4410](https://github.com/echonuit/vigiechiro-pr-companion/issues/4410)) ([8db4524](https://github.com/echonuit/vigiechiro-pr-companion/commit/8db452448f522f95556d4f2379a058780a89c6a6)), closes [#1214](https://github.com/echonuit/vigiechiro-pr-companion/issues/1214) [#4408](https://github.com/echonuit/vigiechiro-pr-companion/issues/4408) [#3668](https://github.com/echonuit/vigiechiro-pr-companion/issues/3668) [#3717](https://github.com/echonuit/vigiechiro-pr-companion/issues/3717)
* **test:** l'inspection s'attend, waitForFxEvents ne l'attendant pas ([#4835](https://github.com/echonuit/vigiechiro-pr-companion/issues/4835)) ([d0bb5c6](https://github.com/echonuit/vigiechiro-pr-companion/commit/d0bb5c650de9ddba1d68de113c8582adf09279cc)), closes [#4811](https://github.com/echonuit/vigiechiro-pr-companion/issues/4811) [#4408](https://github.com/echonuit/vigiechiro-pr-companion/issues/4408) [#4814](https://github.com/echonuit/vigiechiro-pr-companion/issues/4814) [#4804](https://github.com/echonuit/vigiechiro-pr-companion/issues/4804)
* **test:** le clic attend l'écran qu'il ouvre, et l'attente sait lire sur le fil JavaFX ([#4831](https://github.com/echonuit/vigiechiro-pr-companion/issues/4831)) ([a5c6bde](https://github.com/echonuit/vigiechiro-pr-companion/commit/a5c6bde8c15302e81db809d7503d0a2114ff13bc)), closes [#4811](https://github.com/echonuit/vigiechiro-pr-companion/issues/4811) [#4694](https://github.com/echonuit/vigiechiro-pr-companion/issues/4694) [#1214](https://github.com/echonuit/vigiechiro-pr-companion/issues/1214) [#4408](https://github.com/echonuit/vigiechiro-pr-companion/issues/4408) [#4408](https://github.com/echonuit/vigiechiro-pr-companion/issues/4408) [#4819](https://github.com/echonuit/vigiechiro-pr-companion/issues/4819) [#1214](https://github.com/echonuit/vigiechiro-pr-companion/issues/1214) [#4408](https://github.com/echonuit/vigiechiro-pr-companion/issues/4408) [#4804](https://github.com/echonuit/vigiechiro-pr-companion/issues/4804)
* **test:** le fil d'Ariane s'attend lui-même, au lieu d'être déduit du bouton ([#4826](https://github.com/echonuit/vigiechiro-pr-companion/issues/4826)) ([339613c](https://github.com/echonuit/vigiechiro-pr-companion/commit/339613cbe6c0eb7db3c3742ca9b7516bf03c4afc)), closes [#4811](https://github.com/echonuit/vigiechiro-pr-companion/issues/4811) [#4694](https://github.com/echonuit/vigiechiro-pr-companion/issues/4694) [#4819](https://github.com/echonuit/vigiechiro-pr-companion/issues/4819) [#4572](https://github.com/echonuit/vigiechiro-pr-companion/issues/4572) [#4804](https://github.com/echonuit/vigiechiro-pr-companion/issues/4804)
* **test:** les douze derniers bancs referment, et plus aucun ne fuit ([#4930](https://github.com/echonuit/vigiechiro-pr-companion/issues/4930)) ([5dbf502](https://github.com/echonuit/vigiechiro-pr-companion/commit/5dbf502bc405af36b21090dacbe47773e44dd53e)), closes [#4914](https://github.com/echonuit/vigiechiro-pr-companion/issues/4914) [#4785](https://github.com/echonuit/vigiechiro-pr-companion/issues/4785) [#4859](https://github.com/echonuit/vigiechiro-pr-companion/issues/4859)
* **test:** les trois plus gros faiseurs de répertoires temporaires les referment ([#4856](https://github.com/echonuit/vigiechiro-pr-companion/issues/4856)) ([1c8414d](https://github.com/echonuit/vigiechiro-pr-companion/commit/1c8414dc68185da0b744ad2c375da2ee587ae0fa)), closes [#4737](https://github.com/echonuit/vigiechiro-pr-companion/issues/4737) [#4804](https://github.com/echonuit/vigiechiro-pr-companion/issues/4804)
* **test:** quarante et une attentes disent ce qu'elles guettaient, et le cliquet s'élargit ([#4996](https://github.com/echonuit/vigiechiro-pr-companion/issues/4996)) ([b7ec950](https://github.com/echonuit/vigiechiro-pr-companion/commit/b7ec950e1d93ea7beaa98528bb51b2657e6bf220)), closes [#4845](https://github.com/echonuit/vigiechiro-pr-companion/issues/4845) [#4845](https://github.com/echonuit/vigiechiro-pr-companion/issues/4845) [#4974](https://github.com/echonuit/vigiechiro-pr-companion/issues/4974)
* **test:** sept aides cessent d'attendre en silence, et un cliquet lit les corps plutôt que les noms ([#4979](https://github.com/echonuit/vigiechiro-pr-companion/issues/4979)) ([614fb5c](https://github.com/echonuit/vigiechiro-pr-companion/commit/614fb5c92670d8acc72834b338efb5f5d96d1d2e)), closes [#4974](https://github.com/echonuit/vigiechiro-pr-companion/issues/4974) [#4847](https://github.com/echonuit/vigiechiro-pr-companion/issues/4847) [#4974](https://github.com/echonuit/vigiechiro-pr-companion/issues/4974) [#4974](https://github.com/echonuit/vigiechiro-pr-companion/issues/4974)
* **test:** treize bancs cessent de réinventer l'attente, et l'un d'eux apprend à Attente ([#4973](https://github.com/echonuit/vigiechiro-pr-companion/issues/4973)) ([f06c284](https://github.com/echonuit/vigiechiro-pr-companion/commit/f06c284280a7461cf4a51bdf8aabb2011dcc96b2)), closes [#4847](https://github.com/echonuit/vigiechiro-pr-companion/issues/4847)
* **test:** une attente qui attend, là où une respiration de tournage en tenait lieu ([#4819](https://github.com/echonuit/vigiechiro-pr-companion/issues/4819)) ([40d2e43](https://github.com/echonuit/vigiechiro-pr-companion/commit/40d2e43a4f0033bdfbbe13b988463cd0f3d61bfd)), closes [#4811](https://github.com/echonuit/vigiechiro-pr-companion/issues/4811) [#4504](https://github.com/echonuit/vigiechiro-pr-companion/issues/4504) [#4804](https://github.com/echonuit/vigiechiro-pr-companion/issues/4804)


### Features

* **api:** le rayon se serre, et le zéro de gauche revient ([#4597](https://github.com/echonuit/vigiechiro-pr-companion/issues/4597)) ([e85cece](https://github.com/echonuit/vigiechiro-pr-companion/commit/e85cece7aebf3e3b578d2951f8c57c038dfbb6a3)), closes [#4573](https://github.com/echonuit/vigiechiro-pr-companion/issues/4573) [#4576](https://github.com/echonuit/vigiechiro-pr-companion/issues/4576)
* **api:** un refus dit lequel des deux sens il porte ([#4559](https://github.com/echonuit/vigiechiro-pr-companion/issues/4559)) ([27dd689](https://github.com/echonuit/vigiechiro-pr-companion/commit/27dd6892400ccce8184a575c54e18336d757a968)), closes [#4524](https://github.com/echonuit/vigiechiro-pr-companion/issues/4524)
* **banc:** un banc d'étalonnage, et deux verdicts dont un démenti ([#4529](https://github.com/echonuit/vigiechiro-pr-companion/issues/4529)) ([fa6bf0e](https://github.com/echonuit/vigiechiro-pr-companion/commit/fa6bf0e92d6a222f4935c8f7662aaca5118dc7bb)), closes [#4444](https://github.com/echonuit/vigiechiro-pr-companion/issues/4444) [#4444](https://github.com/echonuit/vigiechiro-pr-companion/issues/4444) [#4356](https://github.com/echonuit/vigiechiro-pr-companion/issues/4356) [#4523](https://github.com/echonuit/vigiechiro-pr-companion/issues/4523) [#4356](https://github.com/echonuit/vigiechiro-pr-companion/issues/4356) [#4444](https://github.com/echonuit/vigiechiro-pr-companion/issues/4444) [BancDeRecetteUrlTest#le_banc_ignore_l_url_ambiante](https://github.com/BancDeRecetteUrlTest/issues/le_banc_ignore_l_url_ambiante) [#4332](https://github.com/echonuit/vigiechiro-pr-companion/issues/4332) [#4444](https://github.com/echonuit/vigiechiro-pr-companion/issues/4444) [#4505](https://github.com/echonuit/vigiechiro-pr-companion/issues/4505) [#4444](https://github.com/echonuit/vigiechiro-pr-companion/issues/4444)
* **banc:** une exception morte dans le fil JavaFX fait désormais rougir son cas ([#4412](https://github.com/echonuit/vigiechiro-pr-companion/issues/4412)) ([7a4c943](https://github.com/echonuit/vigiechiro-pr-companion/commit/7a4c9436b5e53118c15cb201c00f31a7998e5756)), closes [#4408](https://github.com/echonuit/vigiechiro-pr-companion/issues/4408) [#4409](https://github.com/echonuit/vigiechiro-pr-companion/issues/4409) [#4408](https://github.com/echonuit/vigiechiro-pr-companion/issues/4408) [#4409](https://github.com/echonuit/vigiechiro-pr-companion/issues/4409)
* **ci:** le jeton d’un tournage meurt avec le run qui s’en est servi ([#4320](https://github.com/echonuit/vigiechiro-pr-companion/issues/4320)) ([c53c4e7](https://github.com/echonuit/vigiechiro-pr-companion/commit/c53c4e7dc67ad89ced17d526b244321c7f675be2)), closes [#4305](https://github.com/echonuit/vigiechiro-pr-companion/issues/4305) [#4291](https://github.com/echonuit/vigiechiro-pr-companion/issues/4291)
* **ci:** les specs principales d'OpenSpec valident, et un corpus vide refuse ([#4963](https://github.com/echonuit/vigiechiro-pr-companion/issues/4963)) ([8db15a7](https://github.com/echonuit/vigiechiro-pr-companion/commit/8db15a750760052450929b6e1cc5dbc1deefdfda))
* **ci:** un jeton a une forme, et les textes du tournage la cherchent avant de partir ([#4369](https://github.com/echonuit/vigiechiro-pr-companion/issues/4369)) ([2cea73b](https://github.com/echonuit/vigiechiro-pr-companion/commit/2cea73b7a39e587a2bc971abf745f32ded25143f)), closes [#4327](https://github.com/echonuit/vigiechiro-pr-companion/issues/4327) [#4291](https://github.com/echonuit/vigiechiro-pr-companion/issues/4291)
* **ci:** un rouge se classe avant de se rejouer, et un sur cinq seulement vaut le rejeu ([#4964](https://github.com/echonuit/vigiechiro-pr-companion/issues/4964)) ([9c33f4b](https://github.com/echonuit/vigiechiro-pr-companion/commit/9c33f4b4d8279e585db6de342b8bad5c2818f67a)), closes [#4187](https://github.com/echonuit/vigiechiro-pr-companion/issues/4187) [#4187](https://github.com/echonuit/vigiechiro-pr-companion/issues/4187)
* **cli:** les quatre gestes de l'emport ont leur commande, et la page dit le geste ([#4798](https://github.com/echonuit/vigiechiro-pr-companion/issues/4798)) ([1cfc686](https://github.com/echonuit/vigiechiro-pr-companion/commit/1cfc686abada6ddef4a4e3b26b3af11ede8aff72))
* **cli:** situer-carre, et les passes de cloture refaites pour de bon ([#4675](https://github.com/echonuit/vigiechiro-pr-companion/issues/4675)) ([7d812f8](https://github.com/echonuit/vigiechiro-pr-companion/commit/7d812f8ad29e32add549bebc9659fe1607062da6)), closes [#4670](https://github.com/echonuit/vigiechiro-pr-companion/issues/4670) [#4619](https://github.com/echonuit/vigiechiro-pr-companion/issues/4619) [#4609](https://github.com/echonuit/vigiechiro-pr-companion/issues/4609) [#4660](https://github.com/echonuit/vigiechiro-pr-companion/issues/4660) [#4573](https://github.com/echonuit/vigiechiro-pr-companion/issues/4573) [#4660](https://github.com/echonuit/vigiechiro-pr-companion/issues/4660) [#4592](https://github.com/echonuit/vigiechiro-pr-companion/issues/4592) [#4576](https://github.com/echonuit/vigiechiro-pr-companion/issues/4576) [#3960](https://github.com/echonuit/vigiechiro-pr-companion/issues/3960)
* **commun:** le carroyage descend au modèle et répond dans les deux sens ([#4630](https://github.com/echonuit/vigiechiro-pr-companion/issues/4630)) ([7048e52](https://github.com/echonuit/vigiechiro-pr-companion/commit/7048e523fa1b880e5c13b2d42c3451a79a35965e)), closes [#325](https://github.com/echonuit/vigiechiro-pr-companion/issues/325) [#4621](https://github.com/echonuit/vigiechiro-pr-companion/issues/4621) [#4573](https://github.com/echonuit/vigiechiro-pr-companion/issues/4573)
* **garde:** l'échec silencieux se lit aussi dans l'arbre de test ([#4485](https://github.com/echonuit/vigiechiro-pr-companion/issues/4485)) ([c588654](https://github.com/echonuit/vigiechiro-pr-companion/commit/c5886547e6893642e33565fc5636f811ba7d2082)), closes [#4462](https://github.com/echonuit/vigiechiro-pr-companion/issues/4462)
* **garde:** l'issue que ferme une demande de fusion appartient à un chantier ([#4863](https://github.com/echonuit/vigiechiro-pr-companion/issues/4863)) ([cb48950](https://github.com/echonuit/vigiechiro-pr-companion/commit/cb4895003c7a12300cbde2415047a88c134405b1)), closes [#4644](https://github.com/echonuit/vigiechiro-pr-companion/issues/4644) [#4829](https://github.com/echonuit/vigiechiro-pr-companion/issues/4829) [#4562](https://github.com/echonuit/vigiechiro-pr-companion/issues/4562) [#N](https://github.com/echonuit/vigiechiro-pr-companion/issues/N) [#4649](https://github.com/echonuit/vigiechiro-pr-companion/issues/4649) [#4860](https://github.com/echonuit/vigiechiro-pr-companion/issues/4860) [#4649](https://github.com/echonuit/vigiechiro-pr-companion/issues/4649) [#4643](https://github.com/echonuit/vigiechiro-pr-companion/issues/4643) [#4829](https://github.com/echonuit/vigiechiro-pr-companion/issues/4829)
* **garde:** la javadoc des tests entre dans la même dette que le reste ([#4467](https://github.com/echonuit/vigiechiro-pr-companion/issues/4467)) ([c35f6f1](https://github.com/echonuit/vigiechiro-pr-companion/commit/c35f6f161fbb26e4915861ebde52a2783c2f2811)), closes [#4394](https://github.com/echonuit/vigiechiro-pr-companion/issues/4394) [#4462](https://github.com/echonuit/vigiechiro-pr-companion/issues/4462)
* **garde:** la mutation des témoins devient mécanique, plus une discipline ([#4491](https://github.com/echonuit/vigiechiro-pr-companion/issues/4491)) ([e33b85e](https://github.com/echonuit/vigiechiro-pr-companion/commit/e33b85ee2144eb6c00adeb59975fe2db8e85a28a)), closes [#4487](https://github.com/echonuit/vigiechiro-pr-companion/issues/4487) [#4490](https://github.com/echonuit/vigiechiro-pr-companion/issues/4490)
* **garde:** le compte des reliquats de /tmp, différentiel et posé autour de la suite ([#4862](https://github.com/echonuit/vigiechiro-pr-companion/issues/4862)) ([c872221](https://github.com/echonuit/vigiechiro-pr-companion/commit/c8722219cecc97a67cbf9c89e3467bf314e57319)), closes [#4737](https://github.com/echonuit/vigiechiro-pr-companion/issues/4737) [#4737](https://github.com/echonuit/vigiechiro-pr-companion/issues/4737) [#4859](https://github.com/echonuit/vigiechiro-pr-companion/issues/4859)
* **garde:** le portail regarde les deux zones, et le code mort compte ([#4633](https://github.com/echonuit/vigiechiro-pr-companion/issues/4633)) ([a5341c1](https://github.com/echonuit/vigiechiro-pr-companion/commit/a5341c1f596d3ddd12d866da82c8e90a9a25da5c)), closes [#4554](https://github.com/echonuit/vigiechiro-pr-companion/issues/4554) [#4617](https://github.com/echonuit/vigiechiro-pr-companion/issues/4617)
* **garde:** le seuil du cliquet javadoc dépend de ce que le bloc surmonte ([#4463](https://github.com/echonuit/vigiechiro-pr-companion/issues/4463)) ([259d841](https://github.com/echonuit/vigiechiro-pr-companion/commit/259d841b4868c6a6bba538dcc6e65b35e476c33a)), closes [#4461](https://github.com/echonuit/vigiechiro-pr-companion/issues/4461) [#4462](https://github.com/echonuit/vigiechiro-pr-companion/issues/4462) [#4394](https://github.com/echonuit/vigiechiro-pr-companion/issues/4394) [#4462](https://github.com/echonuit/vigiechiro-pr-companion/issues/4462)
* **garde:** le zéro des cinq traces d'outil est tenu, avec ses trois exemptions ([#4793](https://github.com/echonuit/vigiechiro-pr-companion/issues/4793)) ([e56a204](https://github.com/echonuit/vigiechiro-pr-companion/commit/e56a204756f0e684ca9d56f8e335796e46241641)), closes [#4776](https://github.com/echonuit/vigiechiro-pr-companion/issues/4776) [#4781](https://github.com/echonuit/vigiechiro-pr-companion/issues/4781) [#4783](https://github.com/echonuit/vigiechiro-pr-companion/issues/4783) [#4748](https://github.com/echonuit/vigiechiro-pr-companion/issues/4748) [#4783](https://github.com/echonuit/vigiechiro-pr-companion/issues/4783)
* **garde:** les deux loupes de code lisent aussi l'arbre de test ([#4495](https://github.com/echonuit/vigiechiro-pr-companion/issues/4495)) ([4eab1bc](https://github.com/echonuit/vigiechiro-pr-companion/commit/4eab1bcb2a06801ac3a1ba9ea81e0cde3dc71a8b)), closes [#4488](https://github.com/echonuit/vigiechiro-pr-companion/issues/4488)
* **garde:** les invocations d'OpenSpec citées dans nos fichiers doivent exister ([#4537](https://github.com/echonuit/vigiechiro-pr-companion/issues/4537)) ([d59ffa4](https://github.com/echonuit/vigiechiro-pr-companion/commit/d59ffa4f433b6d823ac4fa641287c0eb52372aa8)), closes [#4511](https://github.com/echonuit/vigiechiro-pr-companion/issues/4511) [#4514](https://github.com/echonuit/vigiechiro-pr-companion/issues/4514) [#4512](https://github.com/echonuit/vigiechiro-pr-companion/issues/4512)
* **garde:** les seize catch au corps vide ont une voix, et le cliquet devient un refus ([#4619](https://github.com/echonuit/vigiechiro-pr-companion/issues/4619)) ([7c0bb50](https://github.com/echonuit/vigiechiro-pr-companion/commit/7c0bb50c20bdaa36352966ee64a98278efc42457)), closes [#4488](https://github.com/echonuit/vigiechiro-pr-companion/issues/4488) [#4585](https://github.com/echonuit/vigiechiro-pr-companion/issues/4585)
* **garde:** sept gardes de code cessent de ne lire que la production ([#4489](https://github.com/echonuit/vigiechiro-pr-companion/issues/4489)) ([63e8eca](https://github.com/echonuit/vigiechiro-pr-companion/commit/63e8eca0f492b2bc84155df5deab87dee40fc406)), closes [#4488](https://github.com/echonuit/vigiechiro-pr-companion/issues/4488)
* **garde:** trois cliquets de la ligne d'origine, avec les ADR qui les fondent ([#4478](https://github.com/echonuit/vigiechiro-pr-companion/issues/4478)) ([b659638](https://github.com/echonuit/vigiechiro-pr-companion/commit/b65963861d7b1daea16351c3ff18e410f6661dcb)), closes [#4475](https://github.com/echonuit/vigiechiro-pr-companion/issues/4475) [#4476](https://github.com/echonuit/vigiechiro-pr-companion/issues/4476) [#4477](https://github.com/echonuit/vigiechiro-pr-companion/issues/4477) [#1940](https://github.com/echonuit/vigiechiro-pr-companion/issues/1940) [#1967](https://github.com/echonuit/vigiechiro-pr-companion/issues/1967) [#3452](https://github.com/echonuit/vigiechiro-pr-companion/issues/3452) [#4394](https://github.com/echonuit/vigiechiro-pr-companion/issues/4394) [#4462](https://github.com/echonuit/vigiechiro-pr-companion/issues/4462) [#4394](https://github.com/echonuit/vigiechiro-pr-companion/issues/4394)
* **garde:** trois refus de la ligne d'origine arrivent, et l'un trouve deux défauts ([#4474](https://github.com/echonuit/vigiechiro-pr-companion/issues/4474)) ([28ffe23](https://github.com/echonuit/vigiechiro-pr-companion/commit/28ffe230478672ac751c17163cecc9eaa444d874)), closes [#4462](https://github.com/echonuit/vigiechiro-pr-companion/issues/4462) [#4454](https://github.com/echonuit/vigiechiro-pr-companion/issues/4454) [#4462](https://github.com/echonuit/vigiechiro-pr-companion/issues/4462) [#4334](https://github.com/echonuit/vigiechiro-pr-companion/issues/4334)
* **garde:** un bloc relu et gardé volontairement s'inscrit, et son inscription se périme ([#4421](https://github.com/echonuit/vigiechiro-pr-companion/issues/4421)) ([b5f05fd](https://github.com/echonuit/vigiechiro-pr-companion/commit/b5f05fd027c2b62eb7128a902442f20f9bf0ffbd)), closes [#4415](https://github.com/echonuit/vigiechiro-pr-companion/issues/4415) [#4414](https://github.com/echonuit/vigiechiro-pr-companion/issues/4414) [#4394](https://github.com/echonuit/vigiechiro-pr-companion/issues/4394) [#4415](https://github.com/echonuit/vigiechiro-pr-companion/issues/4415)
* **garde:** un cliquet pour les commentaires qui débordent en corps de méthode ([#4473](https://github.com/echonuit/vigiechiro-pr-companion/issues/4473)) ([c5536c4](https://github.com/echonuit/vigiechiro-pr-companion/commit/c5536c47b6c9a60277a9c8c28e852fef186e9b10)), closes [#4472](https://github.com/echonuit/vigiechiro-pr-companion/issues/4472) [#4394](https://github.com/echonuit/vigiechiro-pr-companion/issues/4394) [#4394](https://github.com/echonuit/vigiechiro-pr-companion/issues/4394) [#4462](https://github.com/echonuit/vigiechiro-pr-companion/issues/4462) [#4463](https://github.com/echonuit/vigiechiro-pr-companion/issues/4463)
* **garde:** un lien de javadoc ne se fait plus casser en deux par le formateur ([#4494](https://github.com/echonuit/vigiechiro-pr-companion/issues/4494)) ([7395068](https://github.com/echonuit/vigiechiro-pr-companion/commit/73950682f2160418c95e7678f863d8bc18865f42)), closes [#4493](https://github.com/echonuit/vigiechiro-pr-companion/issues/4493)
* **garde:** un lot ouvert sans critère de fin reçoit un rappel ([#4978](https://github.com/echonuit/vigiechiro-pr-companion/issues/4978)) ([f622a8f](https://github.com/echonuit/vigiechiro-pr-companion/commit/f622a8ff14b26aa228a57f47bde145ff859eb1bf)), closes [#4975](https://github.com/echonuit/vigiechiro-pr-companion/issues/4975) [#4961](https://github.com/echonuit/vigiechiro-pr-companion/issues/4961) [#N](https://github.com/echonuit/vigiechiro-pr-companion/issues/N) [#4961](https://github.com/echonuit/vigiechiro-pr-companion/issues/4961) [#4977](https://github.com/echonuit/vigiechiro-pr-companion/issues/4977) [DocumentationAJourTest#chaque_chiffre_balise_egale_l_inventaire_reel](https://github.com/DocumentationAJourTest/issues/chaque_chiffre_balise_egale_l_inventaire_reel)
* **garde:** un plancher pour les renvois de l'arbre de test, distinct de celui de production ([#4672](https://github.com/echonuit/vigiechiro-pr-companion/issues/4672)) ([cea617e](https://github.com/echonuit/vigiechiro-pr-companion/commit/cea617efa7c75c3b9449fb0ec801a027d6215df5)), closes [#4502](https://github.com/echonuit/vigiechiro-pr-companion/issues/4502) [#4587](https://github.com/echonuit/vigiechiro-pr-companion/issues/4587) [#4646](https://github.com/echonuit/vigiechiro-pr-companion/issues/4646) [#4502](https://github.com/echonuit/vigiechiro-pr-companion/issues/4502)
* **garde:** un plancher tient les renvois que la javadoc porte ([#4396](https://github.com/echonuit/vigiechiro-pr-companion/issues/4396)) ([8452402](https://github.com/echonuit/vigiechiro-pr-companion/commit/8452402521bada0f2558d40ac49672d629dc8777)), closes [#N](https://github.com/echonuit/vigiechiro-pr-companion/issues/N) [#4394](https://github.com/echonuit/vigiechiro-pr-companion/issues/4394) [#4395](https://github.com/echonuit/vigiechiro-pr-companion/issues/4395) [#4394](https://github.com/echonuit/vigiechiro-pr-companion/issues/4394)
* **garde:** un second cliquet mesure ce qui a été lu, pas ce qu'on raccourcit ([#4470](https://github.com/echonuit/vigiechiro-pr-companion/issues/4470)) ([a4fff68](https://github.com/echonuit/vigiechiro-pr-companion/commit/a4fff6846e4736bf0173e251eb36b0f7b44d9f89)), closes [#4468](https://github.com/echonuit/vigiechiro-pr-companion/issues/4468) [#4394](https://github.com/echonuit/vigiechiro-pr-companion/issues/4394) [#4394](https://github.com/echonuit/vigiechiro-pr-companion/issues/4394) [#4462](https://github.com/echonuit/vigiechiro-pr-companion/issues/4462)
* **garde:** une fermeture écrite en français est une promesse que la forge ne tiendra pas ([#4548](https://github.com/echonuit/vigiechiro-pr-companion/issues/4548)) ([dce9797](https://github.com/echonuit/vigiechiro-pr-companion/commit/dce9797d8702cb37f0e2095b499adbce73c970fa)), closes [#N](https://github.com/echonuit/vigiechiro-pr-companion/issues/N) [#N](https://github.com/echonuit/vigiechiro-pr-companion/issues/N) [#N](https://github.com/echonuit/vigiechiro-pr-companion/issues/N) [#N](https://github.com/echonuit/vigiechiro-pr-companion/issues/N) [#4350](https://github.com/echonuit/vigiechiro-pr-companion/issues/4350) [#4506](https://github.com/echonuit/vigiechiro-pr-companion/issues/4506) [#4527](https://github.com/echonuit/vigiechiro-pr-companion/issues/4527) [#4350](https://github.com/echonuit/vigiechiro-pr-companion/issues/4350) [#N](https://github.com/echonuit/vigiechiro-pr-companion/issues/N) [#N](https://github.com/echonuit/vigiechiro-pr-companion/issues/N) [#N](https://github.com/echonuit/vigiechiro-pr-companion/issues/N) [#N](https://github.com/echonuit/vigiechiro-pr-companion/issues/N) [#N](https://github.com/echonuit/vigiechiro-pr-companion/issues/N) [#N](https://github.com/echonuit/vigiechiro-pr-companion/issues/N) [#4546](https://github.com/echonuit/vigiechiro-pr-companion/issues/4546)
* **garde:** une fusion sans verdict se refuse, là où l'ADR 0041 n'assumait que le rouge ([#4594](https://github.com/echonuit/vigiechiro-pr-companion/issues/4594)) ([919bd30](https://github.com/echonuit/vigiechiro-pr-companion/commit/919bd3002e885ff7975d33a907b7726cf6faa863)), closes [#4560](https://github.com/echonuit/vigiechiro-pr-companion/issues/4560) [#4571](https://github.com/echonuit/vigiechiro-pr-companion/issues/4571)
* **garde:** une loupe dit où regarder avant de contracter un bloc ([#4419](https://github.com/echonuit/vigiechiro-pr-companion/issues/4419)) ([5f01bc8](https://github.com/echonuit/vigiechiro-pr-companion/commit/5f01bc8df5c650f983c4002ac8738d79b9b6a561)), closes [#4415](https://github.com/echonuit/vigiechiro-pr-companion/issues/4415) [#4394](https://github.com/echonuit/vigiechiro-pr-companion/issues/4394)
* **loupe:** treize lots ouverts ne disent pas comment on saura qu'ils sont finis ([#4998](https://github.com/echonuit/vigiechiro-pr-companion/issues/4998)) ([7733f8c](https://github.com/echonuit/vigiechiro-pr-companion/commit/7733f8c012bef498e6d45affbb82c7662da1c83e)), closes [#4977](https://github.com/echonuit/vigiechiro-pr-companion/issues/4977) [#4961](https://github.com/echonuit/vigiechiro-pr-companion/issues/4961) [#4845](https://github.com/echonuit/vigiechiro-pr-companion/issues/4845) [#4992](https://github.com/echonuit/vigiechiro-pr-companion/issues/4992)
* **methode:** l'inventaire du portage lit les contrats, pas les noms de fichiers ([#4653](https://github.com/echonuit/vigiechiro-pr-companion/issues/4653)) ([3ac6330](https://github.com/echonuit/vigiechiro-pr-companion/commit/3ac633073d54951f19b4dfa8a52c0478c6d8274f)), closes [#4635](https://github.com/echonuit/vigiechiro-pr-companion/issues/4635) [#4636](https://github.com/echonuit/vigiechiro-pr-companion/issues/4636) [#4634](https://github.com/echonuit/vigiechiro-pr-companion/issues/4634) [#4635](https://github.com/echonuit/vigiechiro-pr-companion/issues/4635)
* **methode:** le lot 0 laisse son ADR, et l'EPIC cesse d'annoncer des questions résolues ([#4638](https://github.com/echonuit/vigiechiro-pr-companion/issues/4638)) ([c1a8524](https://github.com/echonuit/vigiechiro-pr-companion/commit/c1a8524653152b659774dd2dda72fe05a57d9d76)), closes [#4624](https://github.com/echonuit/vigiechiro-pr-companion/issues/4624) [#3848](https://github.com/echonuit/vigiechiro-pr-companion/issues/3848) [#4517](https://github.com/echonuit/vigiechiro-pr-companion/issues/4517)
* **methode:** le premier changement OpenSpec du dépôt, sur le lot 0 de [#3848](https://github.com/echonuit/vigiechiro-pr-companion/issues/3848) ([#4623](https://github.com/echonuit/vigiechiro-pr-companion/issues/4623)) ([c473566](https://github.com/echonuit/vigiechiro-pr-companion/commit/c4735664e8b531fc032f595d91bf1d9b7eafb72c)), closes [#4517](https://github.com/echonuit/vigiechiro-pr-companion/issues/4517)
* **methode:** le releve rend les exemptions, ce qu'un portage efface sans le dire ([#4678](https://github.com/echonuit/vigiechiro-pr-companion/issues/4678)) ([937fe1b](https://github.com/echonuit/vigiechiro-pr-companion/commit/937fe1b143a837cfa145368bbaeb46323a633b6b)), closes [#4635](https://github.com/echonuit/vigiechiro-pr-companion/issues/4635) [#4390](https://github.com/echonuit/vigiechiro-pr-companion/issues/4390) [#4489](https://github.com/echonuit/vigiechiro-pr-companion/issues/4489) [#4495](https://github.com/echonuit/vigiechiro-pr-companion/issues/4495) [#4467](https://github.com/echonuit/vigiechiro-pr-companion/issues/4467) [#4595](https://github.com/echonuit/vigiechiro-pr-companion/issues/4595) [#4637](https://github.com/echonuit/vigiechiro-pr-companion/issues/4637) [#4662](https://github.com/echonuit/vigiechiro-pr-companion/issues/4662) [#4636](https://github.com/echonuit/vigiechiro-pr-companion/issues/4636) [#4637](https://github.com/echonuit/vigiechiro-pr-companion/issues/4637)
* **methode:** ouvrir-une-pr et clore-une-pr, parce qu'une PR en vol ne se surveille pas toute seule ([#4721](https://github.com/echonuit/vigiechiro-pr-companion/issues/4721)) ([5d234ce](https://github.com/echonuit/vigiechiro-pr-companion/commit/5d234ce08d397e987c0f2180368813728d460c6d)), closes [#N](https://github.com/echonuit/vigiechiro-pr-companion/issues/N)
* **methode:** quatre évolutions écrites depuis le lot 1 arrivent ici ([#4454](https://github.com/echonuit/vigiechiro-pr-companion/issues/4454)) ([60705ab](https://github.com/echonuit/vigiechiro-pr-companion/commit/60705ab4826a19dd212acc44e4f1b59909320e32)), closes [#4453](https://github.com/echonuit/vigiechiro-pr-companion/issues/4453) [#4334](https://github.com/echonuit/vigiechiro-pr-companion/issues/4334) [#4334](https://github.com/echonuit/vigiechiro-pr-companion/issues/4334) [#4394](https://github.com/echonuit/vigiechiro-pr-companion/issues/4394)
* **methode:** un cliquet compte les clôtures sans spécification, une loupe montre la couverture ([#4931](https://github.com/echonuit/vigiechiro-pr-companion/issues/4931)) ([aea2646](https://github.com/echonuit/vigiechiro-pr-companion/commit/aea26462249ae97a41b8a08c31a5af137949fac2)), closes [#4841](https://github.com/echonuit/vigiechiro-pr-companion/issues/4841) [#4882](https://github.com/echonuit/vigiechiro-pr-companion/issues/4882)
* **methode:** un lot multi-PR s'ouvre en sous-chantier, et ouvrir un chantier a sa compétence ([#4715](https://github.com/echonuit/vigiechiro-pr-companion/issues/4715)) ([d4c3651](https://github.com/echonuit/vigiechiro-pr-companion/commit/d4c3651386d52a4d7755689c837b2c7394dd08e2)), closes [#4511](https://github.com/echonuit/vigiechiro-pr-companion/issues/4511) [#3848](https://github.com/echonuit/vigiechiro-pr-companion/issues/3848) [#4712](https://github.com/echonuit/vigiechiro-pr-companion/issues/4712) [#4712](https://github.com/echonuit/vigiechiro-pr-companion/issues/4712)
* **methode:** une clôture laisse sa trace, et son absence se voit ([#4695](https://github.com/echonuit/vigiechiro-pr-companion/issues/4695)) ([c0a1445](https://github.com/echonuit/vigiechiro-pr-companion/commit/c0a144501acf6045775fabe61d9d2cff2820b6c3)), closes [#4671](https://github.com/echonuit/vigiechiro-pr-companion/issues/4671) [#4671](https://github.com/echonuit/vigiechiro-pr-companion/issues/4671)
* **openspec:** la ligne de commande s'épingle, et un garde tient sa version ([#4519](https://github.com/echonuit/vigiechiro-pr-companion/issues/4519)) ([bdde62f](https://github.com/echonuit/vigiechiro-pr-companion/commit/bdde62fd064dba1b4dd74a00b4779d7376707769)), closes [#4355](https://github.com/echonuit/vigiechiro-pr-companion/issues/4355) [#4512](https://github.com/echonuit/vigiechiro-pr-companion/issues/4512) [#4511](https://github.com/echonuit/vigiechiro-pr-companion/issues/4511)
* **openspec:** la spécification vivante entre sous garde, et sa configuration porte les règles ([#4526](https://github.com/echonuit/vigiechiro-pr-companion/issues/4526)) ([4042170](https://github.com/echonuit/vigiechiro-pr-companion/commit/404217038bcc4281cf0868e018cc746574d28c0c)), closes [#4513](https://github.com/echonuit/vigiechiro-pr-companion/issues/4513)
* **passage:** l'identité du relecteur s'appose à l'ouverture du paquet, pas au jugement ([#4676](https://github.com/echonuit/vigiechiro-pr-companion/issues/4676)) ([77c9bb9](https://github.com/echonuit/vigiechiro-pr-companion/commit/77c9bb97aa130e3eb62a6adda6937734dc4ffeee)), closes [#4626](https://github.com/echonuit/vigiechiro-pr-companion/issues/4626)
* **passage:** le champ que nous n'avons pas touché se tait, au lieu de bloquer l'envoi ([#4780](https://github.com/echonuit/vigiechiro-pr-companion/issues/4780)) ([15b6c8e](https://github.com/echonuit/vigiechiro-pr-companion/commit/15b6c8e62bcff8baca886757267597b30a8e355e)), closes [#4707](https://github.com/echonuit/vigiechiro-pr-companion/issues/4707) [#4707](https://github.com/echonuit/vigiechiro-pr-companion/issues/4707) [#4777](https://github.com/echonuit/vigiechiro-pr-companion/issues/4777) [#4757](https://github.com/echonuit/vigiechiro-pr-companion/issues/4757)
* **passage:** le dépôt se souvient de ce que la plateforme portait ([#4720](https://github.com/echonuit/vigiechiro-pr-companion/issues/4720)) ([b8a7b46](https://github.com/echonuit/vigiechiro-pr-companion/commit/b8a7b46107cf2ea5a62391906390ce92fadeea42)), closes [#4707](https://github.com/echonuit/vigiechiro-pr-companion/issues/4707) [#4706](https://github.com/echonuit/vigiechiro-pr-companion/issues/4706) [#4706](https://github.com/echonuit/vigiechiro-pr-companion/issues/4706)
* **passage:** le paquet dit ce qu'il emporte, et la nuit reçue refuse un nouveau tirage ([#4719](https://github.com/echonuit/vigiechiro-pr-companion/issues/4719)) ([63aa4ca](https://github.com/echonuit/vigiechiro-pr-companion/commit/63aa4ca745adc789afffd6a2d1896be64bb08f68))
* **passage:** un paquet dit son poids avant de s'écrire, et ce qu'il écrit se relit ([#4668](https://github.com/echonuit/vigiechiro-pr-companion/issues/4668)) ([1103e4f](https://github.com/echonuit/vigiechiro-pr-companion/commit/1103e4f29f1c130a4785f2655c92e8cb7d41f490)), closes [#3848](https://github.com/echonuit/vigiechiro-pr-companion/issues/3848) [#1994](https://github.com/echonuit/vigiechiro-pr-companion/issues/1994) [#808](https://github.com/echonuit/vigiechiro-pr-companion/issues/808) [#4666](https://github.com/echonuit/vigiechiro-pr-companion/issues/4666) [#4625](https://github.com/echonuit/vigiechiro-pr-companion/issues/4625)
* **qualification:** emporter une nuit et ouvrir un paquet reçu, depuis le menu Outils ([#4765](https://github.com/echonuit/vigiechiro-pr-companion/issues/4765)) ([0792b2d](https://github.com/echonuit/vigiechiro-pr-companion/commit/0792b2d3bf47d0dd5a4e0db9ac9bf9906ab98c23))
* **qualification:** l'avis revient signé, et se range à côté sans écraser ([#4789](https://github.com/echonuit/vigiechiro-pr-companion/issues/4789)) ([6222aa8](https://github.com/echonuit/vigiechiro-pr-companion/commit/6222aa844c88f8bb585a43773afe8c3fbc66166a))
* **qualification:** l'écran montre l'avis du relecteur, et rien quand personne n'a relu ([#4742](https://github.com/echonuit/vigiechiro-pr-companion/issues/4742)) ([2ef88a9](https://github.com/echonuit/vigiechiro-pr-companion/commit/2ef88a9bdc9129892d66573a3a2bfc1521d0d0dd))
* **qualification:** la reprise d'un avis planifie avant d'écrire, et refuse plutôt que d'amputer ([#4697](https://github.com/echonuit/vigiechiro-pr-companion/issues/4697)) ([0abe7ce](https://github.com/echonuit/vigiechiro-pr-companion/commit/0abe7cee35429d2a30282ab6f8518e098b0f9848))
* **qualification:** le parcours d'emport s'assemble, et se refuse quand la nuit n'est pas la même ([#4735](https://github.com/echonuit/vigiechiro-pr-companion/issues/4735)) ([f3a8caa](https://github.com/echonuit/vigiechiro-pr-companion/commit/f3a8caa8397e6931fadaf2875af96158c4b35918))
* **qualification:** une séquence porte deux verdicts, le nôtre et celui d'un relecteur ([#4651](https://github.com/echonuit/vigiechiro-pr-companion/issues/4651)) ([29ef3e9](https://github.com/echonuit/vigiechiro-pr-companion/commit/29ef3e90e83326402dd9414c44959c9afef9a4f4)), closes [#4624](https://github.com/echonuit/vigiechiro-pr-companion/issues/4624) [#4624](https://github.com/echonuit/vigiechiro-pr-companion/issues/4624)
* **recette:** la connexion à la vraie plateforme se filme, sans coller de jeton ([#4323](https://github.com/echonuit/vigiechiro-pr-companion/issues/4323)) ([3525f12](https://github.com/echonuit/vigiechiro-pr-companion/commit/3525f1283117b478be665efe28c25b14dd555f7a)), closes [#1369](https://github.com/echonuit/vigiechiro-pr-companion/issues/1369) [#4307](https://github.com/echonuit/vigiechiro-pr-companion/issues/4307) [#4291](https://github.com/echonuit/vigiechiro-pr-companion/issues/4291)
* **recette:** le tournage dit ce qu’il a donné, pas seulement ce qu’il a indexé ([#4358](https://github.com/echonuit/vigiechiro-pr-companion/issues/4358)) ([7a664ef](https://github.com/echonuit/vigiechiro-pr-companion/commit/7a664ef2bcbb4b76abdfbcd42c916c12052a818b)), closes [#4351](https://github.com/echonuit/vigiechiro-pr-companion/issues/4351) [#4326](https://github.com/echonuit/vigiechiro-pr-companion/issues/4326) [#4351](https://github.com/echonuit/vigiechiro-pr-companion/issues/4351) [#4326](https://github.com/echonuit/vigiechiro-pr-companion/issues/4326) [#4291](https://github.com/echonuit/vigiechiro-pr-companion/issues/4291)
* **recette:** les clips connectés ont leur pré-version, et elle ne se compare pas ([#4322](https://github.com/echonuit/vigiechiro-pr-companion/issues/4322)) ([178789e](https://github.com/echonuit/vigiechiro-pr-companion/commit/178789e087c967291b8f4abe67149c063ee1f262)), closes [#4287](https://github.com/echonuit/vigiechiro-pr-companion/issues/4287) [#4258](https://github.com/echonuit/vigiechiro-pr-companion/issues/4258) [#4306](https://github.com/echonuit/vigiechiro-pr-companion/issues/4306) [#4291](https://github.com/echonuit/vigiechiro-pr-companion/issues/4291)
* **recette:** on compare les deux bouts du clip, pas seulement sa fin ([#4308](https://github.com/echonuit/vigiechiro-pr-companion/issues/4308)) ([c3c31ce](https://github.com/echonuit/vigiechiro-pr-companion/commit/c3c31ce9c2df3dd8decdcea6ac43d5c94fe9cc11)), closes [#4297](https://github.com/echonuit/vigiechiro-pr-companion/issues/4297) [#4296](https://github.com/echonuit/vigiechiro-pr-companion/issues/4296) [#4297](https://github.com/echonuit/vigiechiro-pr-companion/issues/4297) [#4295](https://github.com/echonuit/vigiechiro-pr-companion/issues/4295) [#4296](https://github.com/echonuit/vigiechiro-pr-companion/issues/4296) [#4296](https://github.com/echonuit/vigiechiro-pr-companion/issues/4296)
* **recette:** un banc parle à la vraie plateforme sans que le jeton paraisse ([#4318](https://github.com/echonuit/vigiechiro-pr-companion/issues/4318)) ([2fd4dc2](https://github.com/echonuit/vigiechiro-pr-companion/commit/2fd4dc25d66fc224b710e63432a897e8cde7a61d)), closes [#4304](https://github.com/echonuit/vigiechiro-pr-companion/issues/4304) [#4291](https://github.com/echonuit/vigiechiro-pr-companion/issues/4291) [#4303](https://github.com/echonuit/vigiechiro-pr-companion/issues/4303) [#4305](https://github.com/echonuit/vigiechiro-pr-companion/issues/4305)
* **recette:** un écart se lit contre le plancher de son propre cas ([#4290](https://github.com/echonuit/vigiechiro-pr-companion/issues/4290)) ([009a6c1](https://github.com/echonuit/vigiechiro-pr-companion/commit/009a6c1fe97383698a4ef0acdf4a83723bac1519)), closes [#4287](https://github.com/echonuit/vigiechiro-pr-companion/issues/4287) [#4274](https://github.com/echonuit/vigiechiro-pr-companion/issues/4274) [#4287](https://github.com/echonuit/vigiechiro-pr-companion/issues/4287)
* **sites:** déclarer un carré en partant d'un lieu, pas d'un numéro ([#4652](https://github.com/echonuit/vigiechiro-pr-companion/issues/4652)) ([c018cb6](https://github.com/echonuit/vigiechiro-pr-companion/commit/c018cb6550e9437eecb99dfa12dd80d047426c41)), closes [#4573](https://github.com/echonuit/vigiechiro-pr-companion/issues/4573) [#4577](https://github.com/echonuit/vigiechiro-pr-companion/issues/4577) [#3801](https://github.com/echonuit/vigiechiro-pr-companion/issues/3801) [#4577](https://github.com/echonuit/vigiechiro-pr-companion/issues/4577)
* **sites:** lire une position collée depuis une carte ([#4588](https://github.com/echonuit/vigiechiro-pr-companion/issues/4588)) ([2436933](https://github.com/echonuit/vigiechiro-pr-companion/commit/243693339dec4faf497ba1d025fed656bf369260)), closes [#4573](https://github.com/echonuit/vigiechiro-pr-companion/issues/4573)


### Performance Improvements

* **audit:** l’audit complet interrogeait la base onze fois par nuit ([#4286](https://github.com/echonuit/vigiechiro-pr-companion/issues/4286)) ([cda9d89](https://github.com/echonuit/vigiechiro-pr-companion/commit/cda9d89ed97395051e8245bd59ca91ca39640973)), closes [#4280](https://github.com/echonuit/vigiechiro-pr-companion/issues/4280)
* **audit:** l’audit de cohérence lit points et communes par lot ([#4281](https://github.com/echonuit/vigiechiro-pr-companion/issues/4281)) ([4da6301](https://github.com/echonuit/vigiechiro-pr-companion/commit/4da6301afb1e9c17ec96f7c371fb4e3ff03159fc)), closes [#4251](https://github.com/echonuit/vigiechiro-pr-companion/issues/4251) [#4271](https://github.com/echonuit/vigiechiro-pr-companion/issues/4271) [#4278](https://github.com/echonuit/vigiechiro-pr-companion/issues/4278) [#4277](https://github.com/echonuit/vigiechiro-pr-companion/issues/4277) [#4277](https://github.com/echonuit/vigiechiro-pr-companion/issues/4277)
* **chargement:** les deux pistes restantes, chiffrées puis corrigées ([#4285](https://github.com/echonuit/vigiechiro-pr-companion/issues/4285)) ([401d4f1](https://github.com/echonuit/vigiechiro-pr-companion/commit/401d4f1aa94df4e27a7c80de954a679ac790d71a)), closes [#4251](https://github.com/echonuit/vigiechiro-pr-companion/issues/4251) [#4283](https://github.com/echonuit/vigiechiro-pr-companion/issues/4283) [#3458](https://github.com/echonuit/vigiechiro-pr-companion/issues/3458) [#4283](https://github.com/echonuit/vigiechiro-pr-companion/issues/4283)
* **cli:** lister-sites lit ses points par lot, comme l’écran depuis [#4251](https://github.com/echonuit/vigiechiro-pr-companion/issues/4251) ([#4300](https://github.com/echonuit/vigiechiro-pr-companion/issues/4300)) ([620e4c2](https://github.com/echonuit/vigiechiro-pr-companion/commit/620e4c2d66ad430c206c1ec30d28972ff5a8aae3)), closes [#4289](https://github.com/echonuit/vigiechiro-pr-companion/issues/4289) [#4289](https://github.com/echonuit/vigiechiro-pr-companion/issues/4289) [#4289](https://github.com/echonuit/vigiechiro-pr-companion/issues/4289)
* **export:** l’export des sons interrogeait la base trois fois par son ([#4293](https://github.com/echonuit/vigiechiro-pr-companion/issues/4293)) ([e42b2db](https://github.com/echonuit/vigiechiro-pr-companion/commit/e42b2db8a51391adf33fafe22d9a5b24aa85a491)), closes [#4289](https://github.com/echonuit/vigiechiro-pr-companion/issues/4289) [#4289](https://github.com/echonuit/vigiechiro-pr-companion/issues/4289)
* **saison:** « Ma saison » cesse de croître avec l’inventaire ([#4279](https://github.com/echonuit/vigiechiro-pr-companion/issues/4279)) ([b2202cf](https://github.com/echonuit/vigiechiro-pr-companion/commit/b2202cf4585d4c1c0839d825771a29f837e0df0e)), closes [#4251](https://github.com/echonuit/vigiechiro-pr-companion/issues/4251) [#4271](https://github.com/echonuit/vigiechiro-pr-companion/issues/4271) [#4278](https://github.com/echonuit/vigiechiro-pr-companion/issues/4278)

# [2.189.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.188.0...v2.189.0) (2026-08-23)


### Bug Fixes

* **adr:** le harnais des détecteurs les exerce tous, et prouve qu'il le fait ([#4270](https://github.com/echonuit/vigiechiro-pr-companion/issues/4270)) ([9db294c](https://github.com/echonuit/vigiechiro-pr-companion/commit/9db294ccec436a56836a6565929f427ce3d51301)), closes [#4268](https://github.com/echonuit/vigiechiro-pr-companion/issues/4268)
* **ci:** la CI dit en entier ce qu'elle fait, et son inventaire voit le Python ([#4256](https://github.com/echonuit/vigiechiro-pr-companion/issues/4256)) ([ca392df](https://github.com/echonuit/vigiechiro-pr-companion/commit/ca392dfaf309a0ce78449edcd6a7970dc16cc7a5)), closes [#2467](https://github.com/echonuit/vigiechiro-pr-companion/issues/2467) [#4071](https://github.com/echonuit/vigiechiro-pr-companion/issues/4071) [#4255](https://github.com/echonuit/vigiechiro-pr-companion/issues/4255) [#4231](https://github.com/echonuit/vigiechiro-pr-companion/issues/4231) [#4013](https://github.com/echonuit/vigiechiro-pr-companion/issues/4013)
* **ci:** le tournage d'une session filme toute la session ([#4206](https://github.com/echonuit/vigiechiro-pr-companion/issues/4206)) ([832b4fc](https://github.com/echonuit/vigiechiro-pr-companion/commit/832b4fca3d777aff222084a199139ec698aadd0a)), closes [#3791](https://github.com/echonuit/vigiechiro-pr-companion/issues/3791) [#4186](https://github.com/echonuit/vigiechiro-pr-companion/issues/4186) [#4162](https://github.com/echonuit/vigiechiro-pr-companion/issues/4162)
* **graphify:** l'outillage du graphe compte juste ce qu'il cartographie ([#4237](https://github.com/echonuit/vigiechiro-pr-companion/issues/4237)) ([503cf83](https://github.com/echonuit/vigiechiro-pr-companion/commit/503cf8396ee2000e69d03805de81de1a5da46752)), closes [#4231](https://github.com/echonuit/vigiechiro-pr-companion/issues/4231)
* **ihm:** les dialogues parlent français, même sur un poste anglais ([#4230](https://github.com/echonuit/vigiechiro-pr-companion/issues/4230)) ([140200d](https://github.com/echonuit/vigiechiro-pr-companion/commit/140200d4ed5a1288643df42e977c5a34238ff1d7)), closes [#4229](https://github.com/echonuit/vigiechiro-pr-companion/issues/4229) [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133)
* **recette:** « dans le cadre » se mesure sur la zone visible, pas sur la scène ([#4129](https://github.com/echonuit/vigiechiro-pr-companion/issues/4129)) ([58004e6](https://github.com/echonuit/vigiechiro-pr-companion/commit/58004e6f551bd16bf670bd2dccb68664763a76a0))
* **recette:** deux ADR dépendent du banc bash, pas trois ([#4228](https://github.com/echonuit/vigiechiro-pr-companion/issues/4228)) ([1143deb](https://github.com/echonuit/vigiechiro-pr-companion/commit/1143deb257da3868088456f55a4632b3449510ec))
* **recette:** deux clips de la modale disaient autre chose que leur planche ([#4167](https://github.com/echonuit/vigiechiro-pr-companion/issues/4167)) ([ad612ae](https://github.com/echonuit/vigiechiro-pr-companion/commit/ad612aef4191e4ae25e4cade370296e038e2f170)), closes [#4149](https://github.com/echonuit/vigiechiro-pr-companion/issues/4149) [#4158](https://github.com/echonuit/vigiechiro-pr-companion/issues/4158) [#4166](https://github.com/echonuit/vigiechiro-pr-companion/issues/4166)
* **recette:** l'état vide et la modale de connexion laissent le temps de les lire ([#4156](https://github.com/echonuit/vigiechiro-pr-companion/issues/4156)) ([21137b0](https://github.com/echonuit/vigiechiro-pr-companion/commit/21137b06eb25d208be4b2fb8fff44d54da347bfa)), closes [#4128](https://github.com/echonuit/vigiechiro-pr-companion/issues/4128) [#4149](https://github.com/echonuit/vigiechiro-pr-companion/issues/4149) [#4149](https://github.com/echonuit/vigiechiro-pr-companion/issues/4149)
* **recette:** la coupe à l'image affine, elle n'appauvrit pas ([#4125](https://github.com/echonuit/vigiechiro-pr-companion/issues/4125)) ([a847138](https://github.com/echonuit/vigiechiro-pr-companion/commit/a84713883a685b12c6059e609da560e1b7fd2e71)), closes [#4122](https://github.com/echonuit/vigiechiro-pr-companion/issues/4122) [#4124](https://github.com/echonuit/vigiechiro-pr-companion/issues/4124)
* **recette:** la marge de fin d'un clip est bornée par le cas voisin ([#4114](https://github.com/echonuit/vigiechiro-pr-companion/issues/4114)) ([12d3fe7](https://github.com/echonuit/vigiechiro-pr-companion/commit/12d3fe787e732ceeeb7485b309fa84aa104cc92d)), closes [#4113](https://github.com/echonuit/vigiechiro-pr-companion/issues/4113)
* **recette:** la queue d'un clip s'arrête à l'image, pas au repère ([#4123](https://github.com/echonuit/vigiechiro-pr-companion/issues/4123)) ([5e99ad3](https://github.com/echonuit/vigiechiro-pr-companion/commit/5e99ad34582ba5d03ad7e2d54b782ced0b1d8ba0)), closes [#4113](https://github.com/echonuit/vigiechiro-pr-companion/issues/4113) [#4113](https://github.com/echonuit/vigiechiro-pr-companion/issues/4113) [#4122](https://github.com/echonuit/vigiechiro-pr-companion/issues/4122)
* **recette:** le banc dessine dans la typographie du produit, pas dans celle du poste ([#4259](https://github.com/echonuit/vigiechiro-pr-companion/issues/4259)) ([6cf9ce2](https://github.com/echonuit/vigiechiro-pr-companion/commit/6cf9ce2c8c21020502f574c7de5285fb37ccb796)), closes [#4241](https://github.com/echonuit/vigiechiro-pr-companion/issues/4241) [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133)
* **recette:** le banc filmé en Java tourne sous Windows, et trois de ses défauts tombent ([#4183](https://github.com/echonuit/vigiechiro-pr-companion/issues/4183)) ([055d513](https://github.com/echonuit/vigiechiro-pr-companion/commit/055d5135e574b0020b38f1b9ea129dc9ba232a78)), closes [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133) [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133) [#4159](https://github.com/echonuit/vigiechiro-pr-companion/issues/4159) [#4160](https://github.com/echonuit/vigiechiro-pr-companion/issues/4160) [#4161](https://github.com/echonuit/vigiechiro-pr-companion/issues/4161) [#4162](https://github.com/echonuit/vigiechiro-pr-companion/issues/4162) [#4163](https://github.com/echonuit/vigiechiro-pr-companion/issues/4163) [#4164](https://github.com/echonuit/vigiechiro-pr-companion/issues/4164) [#4163](https://github.com/echonuit/vigiechiro-pr-companion/issues/4163)
* **recette:** le cadre se lit sur le fil de JavaFX ([#4211](https://github.com/echonuit/vigiechiro-pr-companion/issues/4211)) ([0a22646](https://github.com/echonuit/vigiechiro-pr-companion/commit/0a2264642dc3d884fe61ee3130e8d968888d1371)), closes [#4200](https://github.com/echonuit/vigiechiro-pr-companion/issues/4200) [#4187](https://github.com/echonuit/vigiechiro-pr-companion/issues/4187) [#4187](https://github.com/echonuit/vigiechiro-pr-companion/issues/4187) [#4187](https://github.com/echonuit/vigiechiro-pr-companion/issues/4187)
* **recette:** le clic qui referme une fenêtre se voit enfin ([#4204](https://github.com/echonuit/vigiechiro-pr-companion/issues/4204)) ([6abaf07](https://github.com/echonuit/vigiechiro-pr-companion/commit/6abaf072b720905b4e37ab2761942e62e88680bc))
* **recette:** le garde des adresses lisait une page sur deux ([#4215](https://github.com/echonuit/vigiechiro-pr-companion/issues/4215)) ([a957248](https://github.com/echonuit/vigiechiro-pr-companion/commit/a957248389e03051b197599d85bfb7f792884029)), closes [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133) [#4056](https://github.com/echonuit/vigiechiro-pr-companion/issues/4056)
* **recette:** le motif d’un blocage passe à l’image, et S1-34 attend ce qu’il affirme ([#4218](https://github.com/echonuit/vigiechiro-pr-companion/issues/4218)) ([d9f8e44](https://github.com/echonuit/vigiechiro-pr-companion/commit/d9f8e4439c4823f4e26ea667754c39915c93ad37)), closes [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133) [#4171](https://github.com/echonuit/vigiechiro-pr-companion/issues/4171) [#4205](https://github.com/echonuit/vigiechiro-pr-companion/issues/4205) [#4219](https://github.com/echonuit/vigiechiro-pr-companion/issues/4219) [#4194](https://github.com/echonuit/vigiechiro-pr-companion/issues/4194) [#4205](https://github.com/echonuit/vigiechiro-pr-companion/issues/4205) [#4210](https://github.com/echonuit/vigiechiro-pr-companion/issues/4210)
* **recette:** le motif général n'est qu'un repli, pas un second téléchargement ([#4276](https://github.com/echonuit/vigiechiro-pr-companion/issues/4276)) ([0bab93c](https://github.com/echonuit/vigiechiro-pr-companion/commit/0bab93c38ad687165728abe34f823b9e40b5c4f7)), closes [#4274](https://github.com/echonuit/vigiechiro-pr-companion/issues/4274)
* **recette:** le voile paraissait hors champ, et l’assertion restait verte ([#4263](https://github.com/echonuit/vigiechiro-pr-companion/issues/4263)) ([1861b7c](https://github.com/echonuit/vigiechiro-pr-companion/commit/1861b7c6dab23cfe8b733c1953b31374119d3921)), closes [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133) [#4172](https://github.com/echonuit/vigiechiro-pr-companion/issues/4172)
* **recette:** les 36 clips Java de la page de comparaison répondaient 404 ([#4222](https://github.com/echonuit/vigiechiro-pr-companion/issues/4222)) ([b8acc4d](https://github.com/echonuit/vigiechiro-pr-companion/commit/b8acc4dda885568746630ae8df4a520773997fc5))
* **recette:** les cinq dernières classes filment un geste, et une cesse de filmer du vide ([#4155](https://github.com/echonuit/vigiechiro-pr-companion/issues/4155)) ([66c4ca2](https://github.com/echonuit/vigiechiro-pr-companion/commit/66c4ca29f51f1fd970c21b0ef8cc18cb3e79c39b)), closes [#4128](https://github.com/echonuit/vigiechiro-pr-companion/issues/4128) [#4149](https://github.com/echonuit/vigiechiro-pr-companion/issues/4149) [#4149](https://github.com/echonuit/vigiechiro-pr-companion/issues/4149)
* **recette:** les deux cas de la modale de connexion se jouent depuis le menu ([#4198](https://github.com/echonuit/vigiechiro-pr-companion/issues/4198)) ([9d0c589](https://github.com/echonuit/vigiechiro-pr-companion/commit/9d0c589b292c55d9a43ab210c4a6348a6619d623)), closes [#4168](https://github.com/echonuit/vigiechiro-pr-companion/issues/4168) [#4170](https://github.com/echonuit/vigiechiro-pr-companion/issues/4170) [#4188](https://github.com/echonuit/vigiechiro-pr-companion/issues/4188)
* **recette:** les premiers gestes d'un scénario ne sont plus perdus ([#4197](https://github.com/echonuit/vigiechiro-pr-companion/issues/4197)) ([56e5b93](https://github.com/echonuit/vigiechiro-pr-companion/commit/56e5b9356fac8827a6d0912b5361b00a65c4e034))
* **recette:** les sept clips de MainViewTest montrent enfin ce qu'ils annoncent ([#4150](https://github.com/echonuit/vigiechiro-pr-companion/issues/4150)) ([5874021](https://github.com/echonuit/vigiechiro-pr-companion/commit/58740218977df6080d39f7ce0ad6c0336ab3f028)), closes [#3773](https://github.com/echonuit/vigiechiro-pr-companion/issues/3773) [#4149](https://github.com/echonuit/vigiechiro-pr-companion/issues/4149)
* **recette:** les six cas de la déclaration d'un carré se jouent sur la fenêtre réelle ([#4184](https://github.com/echonuit/vigiechiro-pr-companion/issues/4184)) ([e21431b](https://github.com/echonuit/vigiechiro-pr-companion/commit/e21431ba9249cae7d24b1b3b3da220d10ac091d5)), closes [#4178](https://github.com/echonuit/vigiechiro-pr-companion/issues/4178) [#4179](https://github.com/echonuit/vigiechiro-pr-companion/issues/4179) [#4180](https://github.com/echonuit/vigiechiro-pr-companion/issues/4180) [#4182](https://github.com/echonuit/vigiechiro-pr-companion/issues/4182) [#4099](https://github.com/echonuit/vigiechiro-pr-companion/issues/4099) [#4178](https://github.com/echonuit/vigiechiro-pr-companion/issues/4178)
* **recette:** les six clips de « Mes sites » montrent le geste, et le cadre cesse de mentir ([#4153](https://github.com/echonuit/vigiechiro-pr-companion/issues/4153)) ([d3c46c4](https://github.com/echonuit/vigiechiro-pr-companion/commit/d3c46c4631032b53b002db4f3c822d391914bffd)), closes [#4128](https://github.com/echonuit/vigiechiro-pr-companion/issues/4128) [#4149](https://github.com/echonuit/vigiechiro-pr-companion/issues/4149)
* **recette:** les six clips de la vérification d'un carré montrent la saisie et le clic ([#4152](https://github.com/echonuit/vigiechiro-pr-companion/issues/4152)) ([3f6912d](https://github.com/echonuit/vigiechiro-pr-companion/commit/3f6912d26c8b5a96118b3f12e4602baa7a64bfb5)), closes [#4128](https://github.com/echonuit/vigiechiro-pr-companion/issues/4128) [#4149](https://github.com/echonuit/vigiechiro-pr-companion/issues/4149)
* **recette:** les trois derniers motifs passent à l’image ([#4227](https://github.com/echonuit/vigiechiro-pr-companion/issues/4227)) ([b1ef1e3](https://github.com/echonuit/vigiechiro-pr-companion/commit/b1ef1e3f91440ba5f1681b3ec33d14533b3bac91)), closes [#4173](https://github.com/echonuit/vigiechiro-pr-companion/issues/4173) [#4182](https://github.com/echonuit/vigiechiro-pr-companion/issues/4182) [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133) [#4173](https://github.com/echonuit/vigiechiro-pr-companion/issues/4173) [#4182](https://github.com/echonuit/vigiechiro-pr-companion/issues/4182)
* **recette:** neuf clics décisifs qui n’existaient sur aucune image ([#4240](https://github.com/echonuit/vigiechiro-pr-companion/issues/4240)) ([2e002da](https://github.com/echonuit/vigiechiro-pr-companion/commit/2e002daeb5d6865da004f31a9543aacad1a950dc)), closes [#4181](https://github.com/echonuit/vigiechiro-pr-companion/issues/4181) [#4177](https://github.com/echonuit/vigiechiro-pr-companion/issues/4177) [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133) [#4177](https://github.com/echonuit/vigiechiro-pr-companion/issues/4177) [#4181](https://github.com/echonuit/vigiechiro-pr-companion/issues/4181)
* **recette:** on lit l'entrée de menu avant qu'elle soit cliquée ([#4208](https://github.com/echonuit/vigiechiro-pr-companion/issues/4208)) ([88436ab](https://github.com/echonuit/vigiechiro-pr-companion/commit/88436ab9c5f69f92d563ab93f8b31dfeed089cba)), closes [#4203](https://github.com/echonuit/vigiechiro-pr-companion/issues/4203)
* **recette:** on presse le raccourci, on ne lance plus son accélérateur ([#4260](https://github.com/echonuit/vigiechiro-pr-companion/issues/4260)) ([69cd69b](https://github.com/echonuit/vigiechiro-pr-companion/commit/69cd69b1422703df393c4907d150016699d150f8)), closes [#4242](https://github.com/echonuit/vigiechiro-pr-companion/issues/4242) [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133)
* **recette:** on voit maintenant CHOISIR dans un menu ([#4226](https://github.com/echonuit/vigiechiro-pr-companion/issues/4226)) ([81e1f80](https://github.com/echonuit/vigiechiro-pr-companion/commit/81e1f8008b0297b298b4282a652f896b1c7cd3f4)), closes [#4177](https://github.com/echonuit/vigiechiro-pr-companion/issues/4177) [#4158](https://github.com/echonuit/vigiechiro-pr-companion/issues/4158) [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133) [#4177](https://github.com/echonuit/vigiechiro-pr-companion/issues/4177)
* **recette:** quatre constats de plus, dont deux qui corrigent la question posée ([#4192](https://github.com/echonuit/vigiechiro-pr-companion/issues/4192)) ([0f4a3ab](https://github.com/echonuit/vigiechiro-pr-companion/commit/0f4a3aba5de0188cede38342915ae7e7ba811612)), closes [#4174](https://github.com/echonuit/vigiechiro-pr-companion/issues/4174) [#4175](https://github.com/echonuit/vigiechiro-pr-companion/issues/4175) [#4176](https://github.com/echonuit/vigiechiro-pr-companion/issues/4176) [#4188](https://github.com/echonuit/vigiechiro-pr-companion/issues/4188) [#1045](https://github.com/echonuit/vigiechiro-pr-companion/issues/1045) [#2558](https://github.com/echonuit/vigiechiro-pr-companion/issues/2558) [#4171](https://github.com/echonuit/vigiechiro-pr-companion/issues/4171) [#4172](https://github.com/echonuit/vigiechiro-pr-companion/issues/4172) [#4173](https://github.com/echonuit/vigiechiro-pr-companion/issues/4173) [#4181](https://github.com/echonuit/vigiechiro-pr-companion/issues/4181) [#789](https://github.com/echonuit/vigiechiro-pr-companion/issues/789) [#4194](https://github.com/echonuit/vigiechiro-pr-companion/issues/4194) [#4171](https://github.com/echonuit/vigiechiro-pr-companion/issues/4171) [#4170](https://github.com/echonuit/vigiechiro-pr-companion/issues/4170) [#4170](https://github.com/echonuit/vigiechiro-pr-companion/issues/4170) [#4194](https://github.com/echonuit/vigiechiro-pr-companion/issues/4194)
* **recette:** S4-33 montre enfin ce que son nom annonce ([#4127](https://github.com/echonuit/vigiechiro-pr-companion/issues/4127)) ([64b90fe](https://github.com/echonuit/vigiechiro-pr-companion/commit/64b90fe895ffd193d4fc3586af90d9ffa68a3f52))
* **recette:** S4-33 se joue sur le vrai écran, pas sur un panneau nu ([#4121](https://github.com/echonuit/vigiechiro-pr-companion/issues/4121)) ([857ad3a](https://github.com/echonuit/vigiechiro-pr-companion/commit/857ad3af50d47585599d9fd725a902a915ed2249)), closes [#4115](https://github.com/echonuit/vigiechiro-pr-companion/issues/4115)
* **recette:** trois clips montrent enfin la cause de ce qu'ils font voir ([#4165](https://github.com/echonuit/vigiechiro-pr-companion/issues/4165)) ([73dc183](https://github.com/echonuit/vigiechiro-pr-companion/commit/73dc18327ea56bbd92bc5ae4cb7e8133d1500cad)), closes [#4149](https://github.com/echonuit/vigiechiro-pr-companion/issues/4149) [#4158](https://github.com/echonuit/vigiechiro-pr-companion/issues/4158)
* **recette:** une plage effondrée par le bornage est écartée, et dite ([#4117](https://github.com/echonuit/vigiechiro-pr-companion/issues/4117)) ([3b4c462](https://github.com/echonuit/vigiechiro-pr-companion/commit/3b4c462bed7dbd13589069a069737b67ebcfa56c)), closes [#4114](https://github.com/echonuit/vigiechiro-pr-companion/issues/4114) [#4116](https://github.com/echonuit/vigiechiro-pr-companion/issues/4116)
* **sites:** « Vérifier sur Vigie-Chiro » se ferme sans jeton ([#4212](https://github.com/echonuit/vigiechiro-pr-companion/issues/4212)) ([cc9e3bc](https://github.com/echonuit/vigiechiro-pr-companion/commit/cc9e3bc32dde80fbb58e8f87a7c1283e4aeb20a0)), closes [#789](https://github.com/echonuit/vigiechiro-pr-companion/issues/789) [#4194](https://github.com/echonuit/vigiechiro-pr-companion/issues/4194) [#4205](https://github.com/echonuit/vigiechiro-pr-companion/issues/4205) [#4210](https://github.com/echonuit/vigiechiro-pr-companion/issues/4210) [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133) [#4194](https://github.com/echonuit/vigiechiro-pr-companion/issues/4194) [#4205](https://github.com/echonuit/vigiechiro-pr-companion/issues/4205)
* **sites:** l’écran voit qu’on a suivi son conseil ([#4207](https://github.com/echonuit/vigiechiro-pr-companion/issues/4207)) ([4000f0c](https://github.com/echonuit/vigiechiro-pr-companion/commit/4000f0c7f250f659374af75e3ec8395147469045)), closes [#4194](https://github.com/echonuit/vigiechiro-pr-companion/issues/4194) [#4171](https://github.com/echonuit/vigiechiro-pr-companion/issues/4171) [#4205](https://github.com/echonuit/vigiechiro-pr-companion/issues/4205) [#4171](https://github.com/echonuit/vigiechiro-pr-companion/issues/4171) [#4194](https://github.com/echonuit/vigiechiro-pr-companion/issues/4194) [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133)
* **sites:** un empêchement dit sa cause, au lieu de les nommer toutes ([#4253](https://github.com/echonuit/vigiechiro-pr-companion/issues/4253)) ([f42ce27](https://github.com/echonuit/vigiechiro-pr-companion/commit/f42ce277835aeeeb2eeec28cdbc907061fc451ac)), closes [#718](https://github.com/echonuit/vigiechiro-pr-companion/issues/718) [#4233](https://github.com/echonuit/vigiechiro-pr-companion/issues/4233) [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133)
* **tests:** deux bancs de chrome prennent une fenêtre à eux, et un garde le tient ([#4135](https://github.com/echonuit/vigiechiro-pr-companion/issues/4135)) ([04266b2](https://github.com/echonuit/vigiechiro-pr-companion/commit/04266b2b4ed28d92b0faca28a085933051fdbb00)), closes [#3452](https://github.com/echonuit/vigiechiro-pr-companion/issues/3452) [#1967](https://github.com/echonuit/vigiechiro-pr-companion/issues/1967) [#4134](https://github.com/echonuit/vigiechiro-pr-companion/issues/4134)
* **tests:** le garde d'ajustabilité du Stage mesure enfin, et trouve un cinquième figeage ([#4146](https://github.com/echonuit/vigiechiro-pr-companion/issues/4146)) ([9f44997](https://github.com/echonuit/vigiechiro-pr-companion/commit/9f449974f724c127d3f4583b3718506cdb29d360)), closes [#1967](https://github.com/echonuit/vigiechiro-pr-companion/issues/1967) [#4134](https://github.com/echonuit/vigiechiro-pr-companion/issues/4134) [#3452](https://github.com/echonuit/vigiechiro-pr-companion/issues/3452) [#1940](https://github.com/echonuit/vigiechiro-pr-companion/issues/1940) [#4145](https://github.com/echonuit/vigiechiro-pr-companion/issues/4145) [#4145](https://github.com/echonuit/vigiechiro-pr-companion/issues/4145)


### Features

* **captures:** la carte déjà préfixée montre enfin son état de nommage ([#4144](https://github.com/echonuit/vigiechiro-pr-companion/issues/4144)) ([7135deb](https://github.com/echonuit/vigiechiro-pr-companion/commit/7135deb66f9829efd2f5bf1f2dbccb470cd61a1b)), closes [#4055](https://github.com/echonuit/vigiechiro-pr-companion/issues/4055) [#3460](https://github.com/echonuit/vigiechiro-pr-companion/issues/3460) [#4141](https://github.com/echonuit/vigiechiro-pr-companion/issues/4141) [#4141](https://github.com/echonuit/vigiechiro-pr-companion/issues/4141)
* **ci:** tourner les clips d'une seule session, sur la plateforme de son choix ([#4190](https://github.com/echonuit/vigiechiro-pr-companion/issues/4190)) ([f2d5789](https://github.com/echonuit/vigiechiro-pr-companion/commit/f2d57896cc0f9e12694b053a639d70405ab9d8cd)), closes [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133)
* **ci:** une PR montre les écrans qu'elle change ([#4120](https://github.com/echonuit/vigiechiro-pr-companion/issues/4120)) ([ad8a52d](https://github.com/echonuit/vigiechiro-pr-companion/commit/ad8a52d39b04546bc16d4b09eede2a124d9dc81f)), closes [#4119](https://github.com/echonuit/vigiechiro-pr-companion/issues/4119)
* **recette:** le banc en Java pur se détecte comme celui des repères ([#4186](https://github.com/echonuit/vigiechiro-pr-companion/issues/4186)) ([21f294b](https://github.com/echonuit/vigiechiro-pr-companion/commit/21f294b9a345c7168a3e7c9e75bcd113708b2be5)), closes [#4162](https://github.com/echonuit/vigiechiro-pr-companion/issues/4162)
* **recette:** le banc Java monte dans le train, à côté du banc bash ([#4269](https://github.com/echonuit/vigiechiro-pr-companion/issues/4269)) ([b9c252a](https://github.com/echonuit/vigiechiro-pr-companion/commit/b9c252a815e9443bae13e13980dd99b1c79f38dc)), closes [#4258](https://github.com/echonuit/vigiechiro-pr-companion/issues/4258)
* **recette:** le banc ne filme que les tests qui citent un cas ([#4185](https://github.com/echonuit/vigiechiro-pr-companion/issues/4185)) ([062e6c6](https://github.com/echonuit/vigiechiro-pr-companion/commit/062e6c6602aa0608286b77d50fd304eef2455bfc))
* **recette:** le carré rattaché montre son badge et son bouton offert ([#4140](https://github.com/echonuit/vigiechiro-pr-companion/issues/4140)) ([bb14ba1](https://github.com/echonuit/vigiechiro-pr-companion/commit/bb14ba1571aa7db67330d7d431d2677e3b99daf4)), closes [#3806](https://github.com/echonuit/vigiechiro-pr-companion/issues/3806) [#4137](https://github.com/echonuit/vigiechiro-pr-companion/issues/4137)
* **recette:** le premier écran se joue enfin, cartes cliquées et menu ouvert ([#4139](https://github.com/echonuit/vigiechiro-pr-companion/issues/4139)) ([72eaabb](https://github.com/echonuit/vigiechiro-pr-companion/commit/72eaabbd0d974af8194a9ed9d1f44da251fbabe5)), closes [#2046](https://github.com/echonuit/vigiechiro-pr-companion/issues/2046) [#4138](https://github.com/echonuit/vigiechiro-pr-companion/issues/4138)
* **recette:** les clips vivent aussi sur le tag de leur version ([#4266](https://github.com/echonuit/vigiechiro-pr-companion/issues/4266)) ([a3b6a65](https://github.com/echonuit/vigiechiro-pr-companion/commit/a3b6a65aa7eec860bf9a1a808691eeb32c4bef18)), closes [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133) [#4258](https://github.com/echonuit/vigiechiro-pr-companion/issues/4258) [#4111](https://github.com/echonuit/vigiechiro-pr-companion/issues/4111) [#4222](https://github.com/echonuit/vigiechiro-pr-companion/issues/4222)
* **recette:** les quatre issues de la connexion se jouent sur le vrai écran ([#4131](https://github.com/echonuit/vigiechiro-pr-companion/issues/4131)) ([49a5f2b](https://github.com/echonuit/vigiechiro-pr-companion/commit/49a5f2b372ac1b155c5a654fb5ead7a8a84f61d3)), closes [#4130](https://github.com/echonuit/vigiechiro-pr-companion/issues/4130) [#btnDeposer](https://github.com/echonuit/vigiechiro-pr-companion/issues/btnDeposer) [#1940](https://github.com/echonuit/vigiechiro-pr-companion/issues/1940) [#1967](https://github.com/echonuit/vigiechiro-pr-companion/issues/1967) [#3452](https://github.com/echonuit/vigiechiro-pr-companion/issues/3452) [#4130](https://github.com/echonuit/vigiechiro-pr-companion/issues/4130)
* **recette:** les quatre zones de la fiche d'un site se jouent sur le vrai écran ([#4136](https://github.com/echonuit/vigiechiro-pr-companion/issues/4136)) ([45ba63d](https://github.com/echonuit/vigiechiro-pr-companion/commit/45ba63d8029a99347fc44e9a041a27407233811c)), closes [#4132](https://github.com/echonuit/vigiechiro-pr-companion/issues/4132)
* **recette:** un banc se déclare, il ne se recopie plus ([#4225](https://github.com/echonuit/vigiechiro-pr-companion/issues/4225)) ([16cfc1c](https://github.com/echonuit/vigiechiro-pr-companion/commit/16cfc1c6142e65d11a8b1cba330faadbc45bdfc5)), closes [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133)
* **recette:** un cas dit où se lit son verdict, et ce que son clip laisse dehors ([#4143](https://github.com/echonuit/vigiechiro-pr-companion/issues/4143)) ([78a5c7d](https://github.com/echonuit/vigiechiro-pr-companion/commit/78a5c7d45410f9fe7ac56c2fe95e3e9755024a57)), closes [#4133](https://github.com/echonuit/vigiechiro-pr-companion/issues/4133) [#4142](https://github.com/echonuit/vigiechiro-pr-companion/issues/4142)
* **recette:** un cas filmé qui ne s'arrête jamais fait rougir la CI ([#4157](https://github.com/echonuit/vigiechiro-pr-companion/issues/4157)) ([3a5518b](https://github.com/echonuit/vigiechiro-pr-companion/commit/3a5518b658b843cddc97d7578205ddbf460ee55e)), closes [#4128](https://github.com/echonuit/vigiechiro-pr-companion/issues/4128) [#4149](https://github.com/echonuit/vigiechiro-pr-companion/issues/4149) [#4149](https://github.com/echonuit/vigiechiro-pr-companion/issues/4149) [#4149](https://github.com/echonuit/vigiechiro-pr-companion/issues/4149)
* **recette:** un clip montre le geste, et plus seulement son effet ([#4193](https://github.com/echonuit/vigiechiro-pr-companion/issues/4193)) ([8a2f002](https://github.com/echonuit/vigiechiro-pr-companion/commit/8a2f00298b2885cee115ca944729bc165da8aba4))
* **recette:** un outil qui dit ce qui a changé entre deux tournages ([#4275](https://github.com/echonuit/vigiechiro-pr-companion/issues/4275)) ([40b6e53](https://github.com/echonuit/vigiechiro-pr-companion/commit/40b6e53e94643fef912b521a2d0947908ec19c54)), closes [#4274](https://github.com/echonuit/vigiechiro-pr-companion/issues/4274) [#4269](https://github.com/echonuit/vigiechiro-pr-companion/issues/4269) [#4274](https://github.com/echonuit/vigiechiro-pr-companion/issues/4274) [#4258](https://github.com/echonuit/vigiechiro-pr-companion/issues/4258) [#4269](https://github.com/echonuit/vigiechiro-pr-companion/issues/4269)
* **recette:** une modale se filme avec l'écran d'où elle part et celui où elle rend ([#4189](https://github.com/echonuit/vigiechiro-pr-companion/issues/4189)) ([ac855bf](https://github.com/echonuit/vigiechiro-pr-companion/commit/ac855bfb5cb85155e51e8e3a4001758410524cbe)), closes [#4174](https://github.com/echonuit/vigiechiro-pr-companion/issues/4174) [#4175](https://github.com/echonuit/vigiechiro-pr-companion/issues/4175) [#4176](https://github.com/echonuit/vigiechiro-pr-companion/issues/4176) [#4188](https://github.com/echonuit/vigiechiro-pr-companion/issues/4188)


### Performance Improvements

* **multisite:** « Carte & passages » cesse de croître avec l’inventaire ([#4277](https://github.com/echonuit/vigiechiro-pr-companion/issues/4277)) ([8d02186](https://github.com/echonuit/vigiechiro-pr-companion/commit/8d021867a239279f788dcda98b51ce122ed6d195)), closes [#4251](https://github.com/echonuit/vigiechiro-pr-companion/issues/4251) [#4271](https://github.com/echonuit/vigiechiro-pr-companion/issues/4271)
* **sites:** le chargement de « Mes sites » cesse de croître avec l’inventaire ([#4273](https://github.com/echonuit/vigiechiro-pr-companion/issues/4273)) ([dcab8a3](https://github.com/echonuit/vigiechiro-pr-companion/commit/dcab8a3ddd3a034864d172852cb8e45520a495bf)), closes [#4251](https://github.com/echonuit/vigiechiro-pr-companion/issues/4251) [#4271](https://github.com/echonuit/vigiechiro-pr-companion/issues/4271) [#4172](https://github.com/echonuit/vigiechiro-pr-companion/issues/4172) [#4271](https://github.com/echonuit/vigiechiro-pr-companion/issues/4271)

# [2.188.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.187.0...v2.188.0) (2026-08-21)


### Bug Fixes

* **commun:** cinq comptes rendus s'ouvrent sans propriétaire, et le bureau les pose où il veut ([#4097](https://github.com/echonuit/vigiechiro-pr-companion/issues/4097)) ([7d5ffca](https://github.com/echonuit/vigiechiro-pr-companion/commit/7d5ffca5eb175c4fa7264eb6eac6b81efffb3066)), closes [#4084](https://github.com/echonuit/vigiechiro-pr-companion/issues/4084) [#4092](https://github.com/echonuit/vigiechiro-pr-companion/issues/4092)
* **deb:** l'entrée de menu ne décide plus du sort de l'installation ([#4096](https://github.com/echonuit/vigiechiro-pr-companion/issues/4096)) ([3ed3bec](https://github.com/echonuit/vigiechiro-pr-companion/commit/3ed3bec856ea55be4c2563a54bb5623abbfc1e6c)), closes [#4071](https://github.com/echonuit/vigiechiro-pr-companion/issues/4071) [#4080](https://github.com/echonuit/vigiechiro-pr-companion/issues/4080) [#4081](https://github.com/echonuit/vigiechiro-pr-companion/issues/4081)
* **flatpak:** le paquet éprouve sa ligne de commande, et la doc date sa promesse ([#4100](https://github.com/echonuit/vigiechiro-pr-companion/issues/4100)) ([45eb7b9](https://github.com/echonuit/vigiechiro-pr-companion/commit/45eb7b9ecff8ba1cf904414c802020318200d031)), closes [#4071](https://github.com/echonuit/vigiechiro-pr-companion/issues/4071) [#4087](https://github.com/echonuit/vigiechiro-pr-companion/issues/4087) [#4071](https://github.com/echonuit/vigiechiro-pr-companion/issues/4071)
* **recette:** le versement des clips retire ceux dont le cas a disparu ([#4107](https://github.com/echonuit/vigiechiro-pr-companion/issues/4107)) ([d289980](https://github.com/echonuit/vigiechiro-pr-companion/commit/d28998080fbb78fdcbbf3e9cc44ee89da19ec4b7)), closes [#4099](https://github.com/echonuit/vigiechiro-pr-companion/issues/4099) [#4091](https://github.com/echonuit/vigiechiro-pr-companion/issues/4091) [#4106](https://github.com/echonuit/vigiechiro-pr-companion/issues/4106)
* **sites:** la fiche rend compte à son bandeau, et non dans une fenêtre ([#4094](https://github.com/echonuit/vigiechiro-pr-companion/issues/4094)) ([c2d8892](https://github.com/echonuit/vigiechiro-pr-companion/commit/c2d889269ce330b4812a284a75583516e463ea82)), closes [#4091](https://github.com/echonuit/vigiechiro-pr-companion/issues/4091)
* **sites:** récupérer un carré reste sur « Mes sites », d'où la modale est partie ([#4102](https://github.com/echonuit/vigiechiro-pr-companion/issues/4102)) ([814e9a8](https://github.com/echonuit/vigiechiro-pr-companion/commit/814e9a8072289e765f0e849f3be8287a684851e2)), closes [#4099](https://github.com/echonuit/vigiechiro-pr-companion/issues/4099) [#4091](https://github.com/echonuit/vigiechiro-pr-companion/issues/4091)


### Features

* **cli:** l'aide nomme le mot qui ouvre la fenêtre, et la clôture comble ses trous ([#4093](https://github.com/echonuit/vigiechiro-pr-companion/issues/4093)) ([4d922b6](https://github.com/echonuit/vigiechiro-pr-companion/commit/4d922b61c5adebf17f3d704bedd872561fddba57)), closes [#2294](https://github.com/echonuit/vigiechiro-pr-companion/issues/2294) [#4071](https://github.com/echonuit/vigiechiro-pr-companion/issues/4071) [#4088](https://github.com/echonuit/vigiechiro-pr-companion/issues/4088) [#4081](https://github.com/echonuit/vigiechiro-pr-companion/issues/4081)
* **cli:** les bornes se relèvent en ligne de commande, plus par une propriété JVM ([#4098](https://github.com/echonuit/vigiechiro-pr-companion/issues/4098)) ([0495161](https://github.com/echonuit/vigiechiro-pr-companion/commit/049516176070bab117d0e0aef25ac1043f1dcc5d)), closes [#4075](https://github.com/echonuit/vigiechiro-pr-companion/issues/4075) [#4075](https://github.com/echonuit/vigiechiro-pr-companion/issues/4075) [#4075](https://github.com/echonuit/vigiechiro-pr-companion/issues/4075)

# [2.187.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.186.0...v2.187.0) (2026-08-21)


### Bug Fixes

* **audio:** le panneau d'écoute tient son contenu, au lieu de l'empiler ([#4025](https://github.com/echonuit/vigiechiro-pr-companion/issues/4025)) ([dc8a395](https://github.com/echonuit/vigiechiro-pr-companion/commit/dc8a395c8e3b699bb9332f36e34b46eca5dc00b1))
* **captures:** la fixture ne passe plus par un chemin prévisible ([#4051](https://github.com/echonuit/vigiechiro-pr-companion/issues/4051)) ([7e2aec7](https://github.com/echonuit/vigiechiro-pr-companion/commit/7e2aec747f928647ff2a4f0f89ce2b80b76e96ec)), closes [#4049](https://github.com/echonuit/vigiechiro-pr-companion/issues/4049) [#4044](https://github.com/echonuit/vigiechiro-pr-companion/issues/4044) [#4044](https://github.com/echonuit/vigiechiro-pr-companion/issues/4044)
* **captures:** le dossier d'une fixture se vide avant d'être écrit ([#4040](https://github.com/echonuit/vigiechiro-pr-companion/issues/4040)) ([f03fd4a](https://github.com/echonuit/vigiechiro-pr-companion/commit/f03fd4a7b285659ae5e31640b0b98f0105832c2c)), closes [#4013](https://github.com/echonuit/vigiechiro-pr-companion/issues/4013)
* **ci:** ffmpeg traîne dix paquets de polices, donc pas de cache de fichiers ([#4039](https://github.com/echonuit/vigiechiro-pr-companion/issues/4039)) ([26cf2d3](https://github.com/echonuit/vigiechiro-pr-companion/commit/26cf2d3be1dcc27c5a408e9d9d8d39df1491694e)), closes [#4036](https://github.com/echonuit/vigiechiro-pr-companion/issues/4036)
* **ci:** la porte cesse d'annoncer un volume qu'elle n'a pas mesuré ([#4041](https://github.com/echonuit/vigiechiro-pr-companion/issues/4041)) ([e67191b](https://github.com/echonuit/vigiechiro-pr-companion/commit/e67191b38eaed067e08c7dd81a6a5d316aa467bf))
* **ci:** la publication ne dépend plus d'un success implicite, et une garde le tient ([#4085](https://github.com/echonuit/vigiechiro-pr-companion/issues/4085)) ([8a3d819](https://github.com/echonuit/vigiechiro-pr-companion/commit/8a3d819936deb804d3f70873e4e183a40512fec7)), closes [#3770](https://github.com/echonuit/vigiechiro-pr-companion/issues/3770) [#4079](https://github.com/echonuit/vigiechiro-pr-companion/issues/4079) [#3770](https://github.com/echonuit/vigiechiro-pr-companion/issues/3770) [#3770](https://github.com/echonuit/vigiechiro-pr-companion/issues/3770)
* **ci:** le garde des booleens ne balayait que les « if: » ([#4069](https://github.com/echonuit/vigiechiro-pr-companion/issues/4069)) ([29dd68f](https://github.com/echonuit/vigiechiro-pr-companion/commit/29dd68ffbb8f8c826638b394849431853d94475c))
* **ci:** une entree booleenne ne se compare pas a une chaine ([#4067](https://github.com/echonuit/vigiechiro-pr-companion/issues/4067)) ([e0a8385](https://github.com/echonuit/vigiechiro-pr-companion/commit/e0a8385415cecf3815d69be40edf73a95310a12a)), closes [#4054](https://github.com/echonuit/vigiechiro-pr-companion/issues/4054) [#4054](https://github.com/echonuit/vigiechiro-pr-companion/issues/4054)
* **cli:** la nuit se lit dans le fuseau de son site, avant d'être coupée ([#4027](https://github.com/echonuit/vigiechiro-pr-companion/issues/4027)) ([5e04d40](https://github.com/echonuit/vigiechiro-pr-companion/commit/5e04d407cc5b24122ee2d35d9222424d22d2aadb)), closes [#1860](https://github.com/echonuit/vigiechiro-pr-companion/issues/1860)
* **commun:** le filet garde le défaut quand le message explose ([#4012](https://github.com/echonuit/vigiechiro-pr-companion/issues/4012)) ([341216d](https://github.com/echonuit/vigiechiro-pr-companion/commit/341216d41432cce2448f6b8d63a4845fdbb8449e)), closes [#3700](https://github.com/echonuit/vigiechiro-pr-companion/issues/3700) [#3956](https://github.com/echonuit/vigiechiro-pr-companion/issues/3956) [#3389](https://github.com/echonuit/vigiechiro-pr-companion/issues/3389) [#3956](https://github.com/echonuit/vigiechiro-pr-companion/issues/3956)
* **commun:** une modale se place elle-meme sur son proprietaire ([#4084](https://github.com/echonuit/vigiechiro-pr-companion/issues/4084)) ([06474f5](https://github.com/echonuit/vigiechiro-pr-companion/commit/06474f5f8a0252274ae9c6b2cc9239a7ce66da93))
* **connexion:** la modale se centre sur l'application, et son clip devient lisible ([#4074](https://github.com/echonuit/vigiechiro-pr-companion/issues/4074)) ([ca9e7b6](https://github.com/echonuit/vigiechiro-pr-companion/commit/ca9e7b6466464bcfa25df679c221cfbfcd2b89f2))
* **ihm:** le socle habille les listes déroulantes, façade et liste déroulée ([#4023](https://github.com/echonuit/vigiechiro-pr-companion/issues/4023)) ([3ef5945](https://github.com/echonuit/vigiechiro-pr-companion/commit/3ef5945d90db2d4f1965c6a524e06b9d360e4641))
* **ihm:** les colonnes de date se lisent en français, et se trient toujours ([#4030](https://github.com/echonuit/vigiechiro-pr-companion/issues/4030)) ([91e8cfb](https://github.com/echonuit/vigiechiro-pr-companion/commit/91e8cfbd76c90e2da17872a93c93654413c745ac))
* **importation:** l'aperçu du préfixe montre un fichier que l'import retiendra ([#4026](https://github.com/echonuit/vigiechiro-pr-companion/issues/4026)) ([7915421](https://github.com/echonuit/vigiechiro-pr-companion/commit/79154214d68b64d315c9a8f7a1d67deec068a79b)), closes [#4013](https://github.com/echonuit/vigiechiro-pr-companion/issues/4013) [#1492](https://github.com/echonuit/vigiechiro-pr-companion/issues/1492) [#4021](https://github.com/echonuit/vigiechiro-pr-companion/issues/4021)
* **recette:** des temps d'arret aux moments cles, et tous les clips sur les pages ([#4070](https://github.com/echonuit/vigiechiro-pr-companion/issues/4070)) ([77e2676](https://github.com/echonuit/vigiechiro-pr-companion/commit/77e26765d1ebc19497148c2d3536c3af8095c3ad))
* **recette:** la caméra centre les fenêtres au lieu de lire leurs coordonnées ([#4065](https://github.com/echonuit/vigiechiro-pr-companion/issues/4065)) ([1a7a04c](https://github.com/echonuit/vigiechiro-pr-companion/commit/1a7a04cbcad173daa20cfb139ba14c510d48b173))
* **recette:** la fenetre hote de S1-26 tient dans l'ecran du banc ([#4078](https://github.com/echonuit/vigiechiro-pr-companion/issues/4078)) ([9c4a19e](https://github.com/echonuit/vigiechiro-pr-companion/commit/9c4a19ef529505285c8a7cb916f7351ad275b259)), closes [#4076](https://github.com/echonuit/vigiechiro-pr-companion/issues/4076)
* **recette:** le banc fixe le placement des fenetres au lieu de l'heriter ([#4076](https://github.com/echonuit/vigiechiro-pr-companion/issues/4076)) ([746d19e](https://github.com/echonuit/vigiechiro-pr-companion/commit/746d19e78c234e1f8157a81cef95244eb5766b72)), closes [#4074](https://github.com/echonuit/vigiechiro-pr-companion/issues/4074)
* **recette:** le clip de S1-26 montre le geste qui ouvre la modale ([#4082](https://github.com/echonuit/vigiechiro-pr-companion/issues/4082)) ([b35f452](https://github.com/echonuit/vigiechiro-pr-companion/commit/b35f452de117b1fa55304f137c08bbea5de730cc))
* **recette:** les six clips restants montrent leurs gestes ([#4086](https://github.com/echonuit/vigiechiro-pr-companion/issues/4086)) ([3f29a07](https://github.com/echonuit/vigiechiro-pr-companion/commit/3f29a07380730224ce3fea400f4f80ed50c97c5b))
* **reglages:** les onglets prennent l'idiome du produit, pas celui de la plateforme ([#4024](https://github.com/echonuit/vigiechiro-pr-companion/issues/4024)) ([8127291](https://github.com/echonuit/vigiechiro-pr-companion/commit/8127291131b3e104d687916fe48d6e5a469e5f4c))


### Features

* **deb:** le paquet pose la commande dans le PATH ([#4080](https://github.com/echonuit/vigiechiro-pr-companion/issues/4080)) ([a00289d](https://github.com/echonuit/vigiechiro-pr-companion/commit/a00289dfdb60bae01c2c1bb8e094adc7a61d5a3f)), closes [#4071](https://github.com/echonuit/vigiechiro-pr-companion/issues/4071)
* **doc:** filmer le seul cas où l'assistant refuse ([#4037](https://github.com/echonuit/vigiechiro-pr-companion/issues/4037)) ([763cc68](https://github.com/echonuit/vigiechiro-pr-companion/commit/763cc68fe227a734d7933a76817fc26d4def66e7)), closes [#4013](https://github.com/echonuit/vigiechiro-pr-companion/issues/4013)
* **doc:** filmer soixante enregistrements, et dire que c'est instantané ([#4058](https://github.com/echonuit/vigiechiro-pr-companion/issues/4058)) ([95e8040](https://github.com/echonuit/vigiechiro-pr-companion/commit/95e804028da0eefcb4b4f8c7747bf3bd19e0925f)), closes [#4013](https://github.com/echonuit/vigiechiro-pr-companion/issues/4013)
* **doc:** filmer trois nuits sur une carte, et celle qu'on retire ([#4057](https://github.com/echonuit/vigiechiro-pr-companion/issues/4057)) ([6080fd1](https://github.com/echonuit/vigiechiro-pr-companion/commit/6080fd1cbeab9f8966b449af4380e7681a82e2e1))
* **doc:** filmer une carte sans journal, et exiger que le film montre l'import ([#4035](https://github.com/echonuit/vigiechiro-pr-companion/issues/4035)) ([838b19d](https://github.com/echonuit/vigiechiro-pr-companion/commit/838b19d4eecb1417e071368432547f59aae0636b)), closes [#4023](https://github.com/echonuit/vigiechiro-pr-companion/issues/4023) [#4013](https://github.com/echonuit/vigiechiro-pr-companion/issues/4013)
* **doc:** le cas « mélange » est filmé, et il a révélé un aperçu trompeur ([#4022](https://github.com/echonuit/vigiechiro-pr-companion/issues/4022)) ([d1679a5](https://github.com/echonuit/vigiechiro-pr-companion/commit/d1679a59abc6b07027b854592eee2f1a7b887b87)), closes [#4013](https://github.com/echonuit/vigiechiro-pr-companion/issues/4013) [#4013](https://github.com/echonuit/vigiechiro-pr-companion/issues/4013) [#3883](https://github.com/echonuit/vigiechiro-pr-companion/issues/3883)
* **installer:** une enveloppe vigiechiro, et la console reste à Windows ([#4077](https://github.com/echonuit/vigiechiro-pr-companion/issues/4077)) ([1be36b2](https://github.com/echonuit/vigiechiro-pr-companion/commit/1be36b2b009f425816cea1766d804a359a073b87)), closes [#4071](https://github.com/echonuit/vigiechiro-pr-companion/issues/4071) [#4075](https://github.com/echonuit/vigiechiro-pr-companion/issues/4075)
* **lanceur:** le point d'entrée empaqueté lit le mot qui ouvre la fenêtre ([#4073](https://github.com/echonuit/vigiechiro-pr-companion/issues/4073)) ([c32ada4](https://github.com/echonuit/vigiechiro-pr-companion/commit/c32ada45844e7bc81e94ff3ea94680f7f3115e78)), closes [#4071](https://github.com/echonuit/vigiechiro-pr-companion/issues/4071)
* **recette:** produire les clips en un tournage, et les ranger hors du depot ([#4064](https://github.com/echonuit/vigiechiro-pr-companion/issues/4064)) ([a170dd0](https://github.com/echonuit/vigiechiro-pr-companion/commit/a170dd0e55877eccdebd19161997d0664fe6173d))
* **recette:** un clip s'ouvre sur un carton qui dit le cas ([#4054](https://github.com/echonuit/vigiechiro-pr-companion/issues/4054)) ([6ee3e5f](https://github.com/echonuit/vigiechiro-pr-companion/commit/6ee3e5f3b70a5d3b906c1110186b8ed52509c79c))

# [2.186.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.185.0...v2.186.0) (2026-08-19)


### Bug Fixes

* **amorcage:** insister quand la cible est tenue, et refuser en le nommant ([#3813](https://github.com/echonuit/vigiechiro-pr-companion/issues/3813)) ([1ebcdde](https://github.com/echonuit/vigiechiro-pr-companion/commit/1ebcdde1577499e22f98b76ab54b27841e44883e)), closes [#3777](https://github.com/echonuit/vigiechiro-pr-companion/issues/3777) [#3802](https://github.com/echonuit/vigiechiro-pr-companion/issues/3802) [#3518](https://github.com/echonuit/vigiechiro-pr-companion/issues/3518) [#3507](https://github.com/echonuit/vigiechiro-pr-companion/issues/3507) [#3777](https://github.com/echonuit/vigiechiro-pr-companion/issues/3777)
* **api:** le push point vers site n’est pas abandonné, la sonde visait la mauvaise route ([#3695](https://github.com/echonuit/vigiechiro-pr-companion/issues/3695)) ([074d3c6](https://github.com/echonuit/vigiechiro-pr-companion/commit/074d3c608353acacbc40469fac2ca48486d1f166))
* **api:** le PUT S3 d'un seul bloc rend son issue, il ne la jette plus ([#3934](https://github.com/echonuit/vigiechiro-pr-companion/issues/3934)) ([8bea8a3](https://github.com/echonuit/vigiechiro-pr-companion/commit/8bea8a3609a0afe3825339f0357ce0db1cd5ee4f)), closes [#1284](https://github.com/echonuit/vigiechiro-pr-companion/issues/1284) [#3687](https://github.com/echonuit/vigiechiro-pr-companion/issues/3687) [#3688](https://github.com/echonuit/vigiechiro-pr-companion/issues/3688)
* **architecture:** le détecteur d'annonces ne dépend plus du séparateur de chemins ([#4018](https://github.com/echonuit/vigiechiro-pr-companion/issues/4018)) ([d6adf4d](https://github.com/echonuit/vigiechiro-pr-companion/commit/d6adf4d5c1e8938d0b963e828722fa8a15df0a18))
* **audio:** déclarer la source que la barre de statut lit vraiment ([#3775](https://github.com/echonuit/vigiechiro-pr-companion/issues/3775)) ([a5e32a3](https://github.com/echonuit/vigiechiro-pr-companion/commit/a5e32a3b6e0ed36fef4ee9cbcc6fa2d4a269e639)), closes [#3548](https://github.com/echonuit/vigiechiro-pr-companion/issues/3548) [#3752](https://github.com/echonuit/vigiechiro-pr-companion/issues/3752)
* **audio:** les commandes de la vue audio se voient comme des boutons ([#3891](https://github.com/echonuit/vigiechiro-pr-companion/issues/3891)) ([ae637bb](https://github.com/echonuit/vigiechiro-pr-companion/commit/ae637bb01bb30419a54760f3019c5803023f4be7)), closes [#6b737b](https://github.com/echonuit/vigiechiro-pr-companion/issues/6b737b) [#3462](https://github.com/echonuit/vigiechiro-pr-companion/issues/3462)
* **captures:** l'aperçu des emplacements montre le produit, plus la machine ([#3815](https://github.com/echonuit/vigiechiro-pr-companion/issues/3815)) ([543c08c](https://github.com/echonuit/vigiechiro-pr-companion/commit/543c08c59827d91b21314e3122245b7e62b55a5e)), closes [#3543](https://github.com/echonuit/vigiechiro-pr-companion/issues/3543) [#3703](https://github.com/echonuit/vigiechiro-pr-companion/issues/3703)
* **captures:** la barre de transport n'est plus rognée, et le panneau des colonnes montre son bouton ([#4010](https://github.com/echonuit/vigiechiro-pr-companion/issues/4010)) ([460e66a](https://github.com/echonuit/vigiechiro-pr-companion/commit/460e66a9062666323bba196a297a790c40a1f58c)), closes [#4003](https://github.com/echonuit/vigiechiro-pr-companion/issues/4003)
* **cli:** chercher un carré côté serveur, et cesser d'en manquer un qui existe ([#3842](https://github.com/echonuit/vigiechiro-pr-companion/issues/3842)) ([58069d9](https://github.com/echonuit/vigiechiro-pr-companion/commit/58069d9e20362fc43d32fe8d8ab84b4cb6bf4e5b)), closes [#1277](https://github.com/echonuit/vigiechiro-pr-companion/issues/1277) [#3769](https://github.com/echonuit/vigiechiro-pr-companion/issues/3769)
* **cli:** creer-site demande à Vigie-Chiro avant de créer ([#3892](https://github.com/echonuit/vigiechiro-pr-companion/issues/3892)) ([f0f686b](https://github.com/echonuit/vigiechiro-pr-companion/commit/f0f686bb0e7a9148cf5755b831ddcf6ef4e7b120)), closes [#3806](https://github.com/echonuit/vigiechiro-pr-companion/issues/3806)
* **cli:** dire l'instant du serveur en français, et à l'heure du lecteur ([#3820](https://github.com/echonuit/vigiechiro-pr-companion/issues/3820)) ([0c7b4d5](https://github.com/echonuit/vigiechiro-pr-companion/commit/0c7b4d5810b78b1fbee85172cdc51db0959fc614)), closes [#3678](https://github.com/echonuit/vigiechiro-pr-companion/issues/3678) [#3678](https://github.com/echonuit/vigiechiro-pr-companion/issues/3678)
* **cli:** la couleur est choisie par le produit, pas déduite par la plateforme ([#3742](https://github.com/echonuit/vigiechiro-pr-companion/issues/3742)) ([ffb9f39](https://github.com/echonuit/vigiechiro-pr-companion/commit/ffb9f39fd2f4353765b3270fe4032a0c5ab27c97)), closes [#3526](https://github.com/echonuit/vigiechiro-pr-companion/issues/3526) [#3738](https://github.com/echonuit/vigiechiro-pr-companion/issues/3738)
* **cli:** le bilan d'import dit ce qu'il reste à faire sur la participation ([#3944](https://github.com/echonuit/vigiechiro-pr-companion/issues/3944)) ([4ba5a15](https://github.com/echonuit/vigiechiro-pr-companion/commit/4ba5a15b8af74ef9424065c22f96835119135800)), closes [#3473](https://github.com/echonuit/vigiechiro-pr-companion/issues/3473) [#1488](https://github.com/echonuit/vigiechiro-pr-companion/issues/1488) [#3942](https://github.com/echonuit/vigiechiro-pr-companion/issues/3942)
* **cli:** le refus ne conseille plus une commande qui n'a jamais existé ([#3980](https://github.com/echonuit/vigiechiro-pr-companion/issues/3980)) ([4ee441d](https://github.com/echonuit/vigiechiro-pr-companion/commit/4ee441dd4b2acf07a98428234a53170b5682bd1a)), closes [#2635](https://github.com/echonuit/vigiechiro-pr-companion/issues/2635) [#3963](https://github.com/echonuit/vigiechiro-pr-companion/issues/3963)
* **cli:** statut-passage lit ses dates en français, son JSON garde l'ISO ([#3997](https://github.com/echonuit/vigiechiro-pr-companion/issues/3997)) ([5698b57](https://github.com/echonuit/vigiechiro-pr-companion/commit/5698b57ef2027385e5a861dd70181f3bb9c86118)), closes [#3950](https://github.com/echonuit/vigiechiro-pr-companion/issues/3950) [#3406](https://github.com/echonuit/vigiechiro-pr-companion/issues/3406) [#3990](https://github.com/echonuit/vigiechiro-pr-companion/issues/3990)
* **commun:** garder l'identité du passage quand l'ouverture d'un écran échoue ([#3736](https://github.com/echonuit/vigiechiro-pr-companion/issues/3736)) ([c911bdf](https://github.com/echonuit/vigiechiro-pr-companion/commit/c911bdfe10b4647617b84b15106ab5d56d50a7ff)), closes [#3546](https://github.com/echonuit/vigiechiro-pr-companion/issues/3546) [#3548](https://github.com/echonuit/vigiechiro-pr-companion/issues/3548)
* **commun:** Habillage garantit le trio, au lieu de croire base.css sur parole ([#3981](https://github.com/echonuit/vigiechiro-pr-companion/issues/3981)) ([94d84d3](https://github.com/echonuit/vigiechiro-pr-companion/commit/94d84d30e8c808b64fdabdfde4bb8ca3149ad386)), closes [#3966](https://github.com/echonuit/vigiechiro-pr-companion/issues/3966) [#3966](https://github.com/echonuit/vigiechiro-pr-companion/issues/3966) [#3978](https://github.com/echonuit/vigiechiro-pr-companion/issues/3978)
* **commun:** la troisième branche de poser regarde ce qui est déjà là ([#3986](https://github.com/echonuit/vigiechiro-pr-companion/issues/3986)) ([5eb3571](https://github.com/echonuit/vigiechiro-pr-companion/commit/5eb3571538f193a44359226cac144d184bc4eadf)), closes [#3985](https://github.com/echonuit/vigiechiro-pr-companion/issues/3985)
* **commun:** tenir le budget horizontal du chrome à la largeur livrée ([#3781](https://github.com/echonuit/vigiechiro-pr-companion/issues/3781)) ([0deb1fb](https://github.com/echonuit/vigiechiro-pr-companion/commit/0deb1fb7dcc7566072f300dd71563b9261d43755)), closes [#3760](https://github.com/echonuit/vigiechiro-pr-companion/issues/3760) [#3743](https://github.com/echonuit/vigiechiro-pr-companion/issues/3743)
* **commun:** un marqueur de champ invalide retient la condition qu'on lui confie ([#3907](https://github.com/echonuit/vigiechiro-pr-companion/issues/3907)) ([ccf6c36](https://github.com/echonuit/vigiechiro-pr-companion/commit/ccf6c3609568238885cc936307b6e6c13236d864)), closes [#3647](https://github.com/echonuit/vigiechiro-pr-companion/issues/3647) [#3647](https://github.com/echonuit/vigiechiro-pr-companion/issues/3647)
* **depot:** le bilan porte la cause de chaque échec, et cesse de promettre l'impossible ([#3976](https://github.com/echonuit/vigiechiro-pr-companion/issues/3976)) ([378988f](https://github.com/echonuit/vigiechiro-pr-companion/commit/378988f594f5f604d1cc281ba9968cdb098f53e5)), closes [#3687](https://github.com/echonuit/vigiechiro-pr-companion/issues/3687) [#3688](https://github.com/echonuit/vigiechiro-pr-companion/issues/3688) [#3688](https://github.com/echonuit/vigiechiro-pr-companion/issues/3688) [#3689](https://github.com/echonuit/vigiechiro-pr-companion/issues/3689) [#3962](https://github.com/echonuit/vigiechiro-pr-companion/issues/3962)
* **depot:** le plan retient si un échec valait la peine d’être retenté ([#3682](https://github.com/echonuit/vigiechiro-pr-companion/issues/3682)) ([be3b6d4](https://github.com/echonuit/vigiechiro-pr-companion/commit/be3b6d496430078e2747fe6fa8faa2f0fa9c2a6e))
* **depot:** un contenu refusé n'était pas coincé, et trois surfaces disaient le contraire ([#4006](https://github.com/echonuit/vigiechiro-pr-companion/issues/4006)) ([83b45b0](https://github.com/echonuit/vigiechiro-pr-companion/commit/83b45b0a0fd5ccdf7b14f90f3c948ba8501250f2)), closes [#3946](https://github.com/echonuit/vigiechiro-pr-companion/issues/3946) [#3687](https://github.com/echonuit/vigiechiro-pr-companion/issues/3687) [#3946](https://github.com/echonuit/vigiechiro-pr-companion/issues/3946)
* **depot:** un refus définitif cesse de promettre une reprise ([#3939](https://github.com/echonuit/vigiechiro-pr-companion/issues/3939)) ([1a9119e](https://github.com/echonuit/vigiechiro-pr-companion/commit/1a9119e7ebc02e931b420687f04127aa46711c9b)), closes [#3688](https://github.com/echonuit/vigiechiro-pr-companion/issues/3688) [#3687](https://github.com/echonuit/vigiechiro-pr-companion/issues/3687)
* **doc:** l'appariement du libellé tolère l'espace que l'OCR perd ([#3958](https://github.com/echonuit/vigiechiro-pr-companion/issues/3958)) ([1aa5ade](https://github.com/echonuit/vigiechiro-pr-companion/commit/1aa5ade635c29343057c8a059ac5f8d5c48d125f)), closes [#3887](https://github.com/echonuit/vigiechiro-pr-companion/issues/3887)
* **e2e:** attendre l'état asynchrone avant d'affirmer ([#3716](https://github.com/echonuit/vigiechiro-pr-companion/issues/3716)) ([05dd7bb](https://github.com/echonuit/vigiechiro-pr-companion/commit/05dd7bb61d942efef3413c0929f935be7d3539eb)), closes [#3668](https://github.com/echonuit/vigiechiro-pr-companion/issues/3668)
* **e2e:** attendre l'état asynchrone dans les 4 derniers parcours exposés ([#3722](https://github.com/echonuit/vigiechiro-pr-companion/issues/3722)) ([23813af](https://github.com/echonuit/vigiechiro-pr-companion/commit/23813afb35ea21b3055932873d576761d20b917d)), closes [#3717](https://github.com/echonuit/vigiechiro-pr-companion/issues/3717) [#3717](https://github.com/echonuit/vigiechiro-pr-companion/issues/3717) [#3716](https://github.com/echonuit/vigiechiro-pr-companion/issues/3716)
* **e2e:** attendre l'état asynchrone dans ParcoursPublierCorrectionsE2ETest ([#3733](https://github.com/echonuit/vigiechiro-pr-companion/issues/3733)) ([#3741](https://github.com/echonuit/vigiechiro-pr-companion/issues/3741)) ([56f0425](https://github.com/echonuit/vigiechiro-pr-companion/commit/56f04251333e2aeefd4340dbe5972f145dacb228)), closes [#3668](https://github.com/echonuit/vigiechiro-pr-companion/issues/3668) [#3717](https://github.com/echonuit/vigiechiro-pr-companion/issues/3717)
* **e2e:** attendre que la carte d'accueil soit visible avant de cliquer ([#3852](https://github.com/echonuit/vigiechiro-pr-companion/issues/3852)) ([8672f07](https://github.com/echonuit/vigiechiro-pr-companion/commit/8672f074bb39792386dd36075ae7ea22527158ae)), closes [#3717](https://github.com/echonuit/vigiechiro-pr-companion/issues/3717) [#3823](https://github.com/echonuit/vigiechiro-pr-companion/issues/3823) [#3823](https://github.com/echonuit/vigiechiro-pr-companion/issues/3823)
* **e2e:** un helper qui abandonne le dit, au lieu de rendre la main ([#3870](https://github.com/echonuit/vigiechiro-pr-companion/issues/3870)) ([ff86cde](https://github.com/echonuit/vigiechiro-pr-companion/commit/ff86cdefaf6855cac624000528be4d7e6f34f77d)), closes [#3823](https://github.com/echonuit/vigiechiro-pr-companion/issues/3823) [#3823](https://github.com/echonuit/vigiechiro-pr-companion/issues/3823)
* **flatpak:** configurer un pinentry en boucle locale pour la signature ostree ([#3790](https://github.com/echonuit/vigiechiro-pr-companion/issues/3790)) ([ba90429](https://github.com/echonuit/vigiechiro-pr-companion/commit/ba904296962bf7b96231ca412dddc5ed19576e5d))
* **flatpak:** ouvrir une fenêtre sous Wayland, et garder cette propriété ([#3673](https://github.com/echonuit/vigiechiro-pr-companion/issues/3673)) ([21dcea4](https://github.com/echonuit/vigiechiro-pr-companion/commit/21dcea4713f897e43f0e38dd1ada24c68e10be2c)), closes [#2191](https://github.com/echonuit/vigiechiro-pr-companion/issues/2191)
* **flatpak:** tolérer un artefact de fin dans le secret FLATPAK_GPG_KEY au décodage ([#3786](https://github.com/echonuit/vigiechiro-pr-companion/issues/3786)) ([ab16971](https://github.com/echonuit/vigiechiro-pr-companion/commit/ab16971ae98fed545806d65ec90a16595f7f1bce))
* **ihm:** l'alerte d'incident montre la panne, pas son enveloppe ([#3894](https://github.com/echonuit/vigiechiro-pr-companion/issues/3894)) ([e030564](https://github.com/echonuit/vigiechiro-pr-companion/commit/e03056474d9f95220e1355749e5c34fd566e86a5)), closes [#3700](https://github.com/echonuit/vigiechiro-pr-companion/issues/3700) [#3470](https://github.com/echonuit/vigiechiro-pr-companion/issues/3470)
* **ihm:** le filet global ne se rejoue plus sur son propre échec ([#3701](https://github.com/echonuit/vigiechiro-pr-companion/issues/3701)) ([0bc14f5](https://github.com/echonuit/vigiechiro-pr-companion/commit/0bc14f5f633e683b27083d0392b3ebad35efe623))
* **ihm:** tout bouton porte une classe du socle, et se reconnaît comme le produit ([#4003](https://github.com/echonuit/vigiechiro-pr-companion/issues/4003)) ([3d147d2](https://github.com/echonuit/vigiechiro-pr-companion/commit/3d147d275cc764c4360b6e283effb3ba0eb41f3d)), closes [#3973](https://github.com/echonuit/vigiechiro-pr-companion/issues/3973) [#3952](https://github.com/echonuit/vigiechiro-pr-companion/issues/3952) [#4002](https://github.com/echonuit/vigiechiro-pr-companion/issues/4002) [#4002](https://github.com/echonuit/vigiechiro-pr-companion/issues/4002)
* **ihm:** trois écrans relisent leur donnée au retour, au lieu de la conserver ([#3993](https://github.com/echonuit/vigiechiro-pr-companion/issues/3993)) ([53a0280](https://github.com/echonuit/vigiechiro-pr-companion/commit/53a0280ed422099ffa8a2cdcbe7117b5a68937fb)), closes [#3900](https://github.com/echonuit/vigiechiro-pr-companion/issues/3900) [#3964](https://github.com/echonuit/vigiechiro-pr-companion/issues/3964)
* **importation:** chaque nuit reçoit les paramètres de sa propre session ([#3899](https://github.com/echonuit/vigiechiro-pr-companion/issues/3899)) ([891e0f2](https://github.com/echonuit/vigiechiro-pr-companion/commit/891e0f216c8ec5211cc930390305caf957a8ebe6)), closes [#1696](https://github.com/echonuit/vigiechiro-pr-companion/issues/1696) [#3460](https://github.com/echonuit/vigiechiro-pr-companion/issues/3460) [#2868](https://github.com/echonuit/vigiechiro-pr-companion/issues/2868)
* **importation:** le compte rendu d'import écrit sa date en français ([#3971](https://github.com/echonuit/vigiechiro-pr-companion/issues/3971)) ([9d7c7d1](https://github.com/echonuit/vigiechiro-pr-companion/commit/9d7c7d13daa1cfe541acfeb751cec1463c277901)), closes [#3424](https://github.com/echonuit/vigiechiro-pr-companion/issues/3424) [#1468](https://github.com/echonuit/vigiechiro-pr-companion/issues/1468) [#3950](https://github.com/echonuit/vigiechiro-pr-companion/issues/3950)
* **lot:** déclarer le lancement dans le binding de la barre de statut ([#3723](https://github.com/echonuit/vigiechiro-pr-companion/issues/3723)) ([fe571fe](https://github.com/echonuit/vigiechiro-pr-companion/commit/fe571fefd9561194455ecb87d52719a4ebd92cdf)), closes [#3546](https://github.com/echonuit/vigiechiro-pr-companion/issues/3546)
* **lot:** le chemin de dépôt se copie, il ne se recopie plus à l'œil ([#3881](https://github.com/echonuit/vigiechiro-pr-companion/issues/3881)) ([64538c7](https://github.com/echonuit/vigiechiro-pr-companion/commit/64538c729389c6c361cf6bf665b037b9b07088f2)), closes [#3464](https://github.com/echonuit/vigiechiro-pr-companion/issues/3464)
* **maj:** la mise à jour dit de fermer l'application avant d'installer ([#3877](https://github.com/echonuit/vigiechiro-pr-companion/issues/3877)) ([6c193b5](https://github.com/echonuit/vigiechiro-pr-companion/commit/6c193b50b63f4bcdc440b9e07cf059f37373fa14)), closes [#3457](https://github.com/echonuit/vigiechiro-pr-companion/issues/3457) [#3621](https://github.com/echonuit/vigiechiro-pr-companion/issues/3621)
* **passage:** le bouton Retour annonce le numéro d'après le renommage, pas celui d'avant ([#3666](https://github.com/echonuit/vigiechiro-pr-companion/issues/3666)) ([3c0473d](https://github.com/echonuit/vigiechiro-pr-companion/commit/3c0473d109c0843545c1b63b39281d0fd10ad722)), closes [#3536](https://github.com/echonuit/vigiechiro-pr-companion/issues/3536) [#3455](https://github.com/echonuit/vigiechiro-pr-companion/issues/3455)
* **passage:** le refus du dépôt conseille le geste qui ramène ce carré-là ([#3863](https://github.com/echonuit/vigiechiro-pr-companion/issues/3863)) ([f590293](https://github.com/echonuit/vigiechiro-pr-companion/commit/f590293f95a936d12228707acb01904e3ee9e541)), closes [#3458](https://github.com/echonuit/vigiechiro-pr-companion/issues/3458)
* **passage:** une nuit hydratée par la synchro cesse de rester à zéro séquence ([#3631](https://github.com/echonuit/vigiechiro-pr-companion/issues/3631)) ([f758d62](https://github.com/echonuit/vigiechiro-pr-companion/commit/f758d623634ef16a3f5d6879b2c9908308315c27)), closes [#3626](https://github.com/echonuit/vigiechiro-pr-companion/issues/3626)
* **persistance:** un parcours de dossier dit ce qu'il n'a pas pu lire ([#3670](https://github.com/echonuit/vigiechiro-pr-companion/issues/3670)) ([b4cd76a](https://github.com/echonuit/vigiechiro-pr-companion/commit/b4cd76a27d4ff3febf9ff78fa5eb79d965bc4f5a)), closes [#3632](https://github.com/echonuit/vigiechiro-pr-companion/issues/3632) [#3610](https://github.com/echonuit/vigiechiro-pr-companion/issues/3610) [#3632](https://github.com/echonuit/vigiechiro-pr-companion/issues/3632)
* **plateformes:** la conclusion du tri suit le périmètre qu'il annonce ([#3761](https://github.com/echonuit/vigiechiro-pr-companion/issues/3761)) ([6b45e7c](https://github.com/echonuit/vigiechiro-pr-companion/commit/6b45e7c37050d0c3b6f8ef9da737b4afb70137b2)), closes [#3754](https://github.com/echonuit/vigiechiro-pr-companion/issues/3754)
* **presence:** tenir le repli promis quand le listage échoue en cours d'itération ([#3858](https://github.com/echonuit/vigiechiro-pr-companion/issues/3858)) ([87fd6dd](https://github.com/echonuit/vigiechiro-pr-companion/commit/87fd6dd072c04f47b9fb256f17df207b4eb1aeff)), closes [#3795](https://github.com/echonuit/vigiechiro-pr-companion/issues/3795) [#3632](https://github.com/echonuit/vigiechiro-pr-companion/issues/3632) [#3525](https://github.com/echonuit/vigiechiro-pr-companion/issues/3525) [#3681](https://github.com/echonuit/vigiechiro-pr-companion/issues/3681) [#3795](https://github.com/echonuit/vigiechiro-pr-companion/issues/3795)
* **recette:** couper le noir par la luminance, et non par des plages continues ([#3737](https://github.com/echonuit/vigiechiro-pr-companion/issues/3737)) ([e6cd6b2](https://github.com/echonuit/vigiechiro-pr-companion/commit/e6cd6b219995881f5127f8f62db19daafe278044)), closes [#3696](https://github.com/echonuit/vigiechiro-pr-companion/issues/3696) [#3707](https://github.com/echonuit/vigiechiro-pr-companion/issues/3707)
* **recette:** la page ne porte plus qu'un seul inventaire des sessions ([#3920](https://github.com/echonuit/vigiechiro-pr-companion/issues/3920)) ([f47583c](https://github.com/echonuit/vigiechiro-pr-companion/commit/f47583cdb6bbf81a0d67bf4c1f88081adf2c4ad2)), closes [#3885](https://github.com/echonuit/vigiechiro-pr-companion/issues/3885) [#3885](https://github.com/echonuit/vigiechiro-pr-companion/issues/3885) [#3787](https://github.com/echonuit/vigiechiro-pr-companion/issues/3787) [#3458](https://github.com/echonuit/vigiechiro-pr-companion/issues/3458) [#3806](https://github.com/echonuit/vigiechiro-pr-companion/issues/3806)
* **recette:** le garde annonce son assiette et nomme les sessions qu'il ne lit pas ([#3918](https://github.com/echonuit/vigiechiro-pr-companion/issues/3918)) ([c34452b](https://github.com/echonuit/vigiechiro-pr-companion/commit/c34452be0081d5e07f72f78a48bd8e8407fce81e)), closes [#3885](https://github.com/echonuit/vigiechiro-pr-companion/issues/3885) [#3884](https://github.com/echonuit/vigiechiro-pr-companion/issues/3884) [#3667](https://github.com/echonuit/vigiechiro-pr-companion/issues/3667)
* **recette:** le journal décrit la séance entière, pas seulement les cas cités ([#3783](https://github.com/echonuit/vigiechiro-pr-companion/issues/3783)) ([05945b5](https://github.com/echonuit/vigiechiro-pr-companion/commit/05945b52bf0f7d65f2f76229327e87acf05b0262)), closes [#3774](https://github.com/echonuit/vigiechiro-pr-companion/issues/3774)
* **recette:** un banc qui maximise tout ne montre pas ce qu'on livre ([#3800](https://github.com/echonuit/vigiechiro-pr-companion/issues/3800)) ([fff3e32](https://github.com/echonuit/vigiechiro-pr-companion/commit/fff3e32ba766a2385beb1e543563a513895a8ef7)), closes [#3774](https://github.com/echonuit/vigiechiro-pr-companion/issues/3774) [#3788](https://github.com/echonuit/vigiechiro-pr-companion/issues/3788) [#3788](https://github.com/echonuit/vigiechiro-pr-companion/issues/3788)
* **reglages:** « Rétablir les emplacements par défaut » suit ce qui a été écrit ([#3660](https://github.com/echonuit/vigiechiro-pr-companion/issues/3660)) ([8a003c7](https://github.com/echonuit/vigiechiro-pr-companion/commit/8a003c79c1648ebbc5463f28f4e0ae0a6b0c430e)), closes [#3543](https://github.com/echonuit/vigiechiro-pr-companion/issues/3543)
* **reglages:** l'écran des Réglages charge enfin la feuille du socle ([#3967](https://github.com/echonuit/vigiechiro-pr-companion/issues/3967)) ([3457ffe](https://github.com/echonuit/vigiechiro-pr-companion/commit/3457ffe96510e6598d5b63a61bfd3bf817e409dd)), closes [#3952](https://github.com/echonuit/vigiechiro-pr-companion/issues/3952) [#3966](https://github.com/echonuit/vigiechiro-pr-companion/issues/3966)
* **reglages:** l'onglet Fonctionnalités se trie sur ce qu'on lit ([#3874](https://github.com/echonuit/vigiechiro-pr-companion/issues/3874)) ([25039cd](https://github.com/echonuit/vigiechiro-pr-companion/commit/25039cd0c314a1d072613bd618fd9eaf4fcd9363))
* **reglages:** le bouton « Copier » se reconnaît d'un écran à l'autre ([#3973](https://github.com/echonuit/vigiechiro-pr-companion/issues/3973)) ([f44385c](https://github.com/echonuit/vigiechiro-pr-companion/commit/f44385c4859eb79281ef877c6dc9b37b1f7e82f3)), closes [#3464](https://github.com/echonuit/vigiechiro-pr-companion/issues/3464) [#3882](https://github.com/echonuit/vigiechiro-pr-companion/issues/3882) [#3966](https://github.com/echonuit/vigiechiro-pr-companion/issues/3966) [#3952](https://github.com/echonuit/vigiechiro-pr-companion/issues/3952)
* **reglages:** les deux chemins de l'onglet Emplacements se copient ([#3905](https://github.com/echonuit/vigiechiro-pr-companion/issues/3905)) ([e377ae1](https://github.com/echonuit/vigiechiro-pr-companion/commit/e377ae1df78cc1bfc8be8c6b0a67edca235ff6c9)), closes [#3464](https://github.com/echonuit/vigiechiro-pr-companion/issues/3464) [#3464](https://github.com/echonuit/vigiechiro-pr-companion/issues/3464) [#3464](https://github.com/echonuit/vigiechiro-pr-companion/issues/3464) [#3882](https://github.com/echonuit/vigiechiro-pr-companion/issues/3882)
* **saison:** le filtre de campagne reparaît quand la première campagne naît après l'ouverture ([#3663](https://github.com/echonuit/vigiechiro-pr-companion/issues/3663)) ([4b44a59](https://github.com/echonuit/vigiechiro-pr-companion/commit/4b44a594773b97ef47dc76b47d06207f3ae068ca)), closes [#3544](https://github.com/echonuit/vigiechiro-pr-companion/issues/3544)
* **sauvegarde:** mesurer tout le reste quand un dossier ne s’ouvre pas ([#3637](https://github.com/echonuit/vigiechiro-pr-companion/issues/3637)) ([ccd380d](https://github.com/echonuit/vigiechiro-pr-companion/commit/ccd380daf1ff3671415d11fd1e309d12e49738b2)), closes [#3627](https://github.com/echonuit/vigiechiro-pr-companion/issues/3627) [#3627](https://github.com/echonuit/vigiechiro-pr-companion/issues/3627)
* **sauvegarde:** refuser quand la pesée n’a pas tout vu, au lieu de conclure ([#3630](https://github.com/echonuit/vigiechiro-pr-companion/issues/3630)) ([054c3e1](https://github.com/echonuit/vigiechiro-pr-companion/commit/054c3e13aa8e05978a153e15c6f0b5e95ec32c2d))
* **sites:** la fiche relit son carré après un renommage, au lieu d'annoncer l'ancien numéro ([#3685](https://github.com/echonuit/vigiechiro-pr-companion/issues/3685)) ([5dfa054](https://github.com/echonuit/vigiechiro-pr-companion/commit/5dfa05461d3e04c56914e52ceec1f27e0574a940)), closes [#3539](https://github.com/echonuit/vigiechiro-pr-companion/issues/3539) [#3455](https://github.com/echonuit/vigiechiro-pr-companion/issues/3455) [#3455](https://github.com/echonuit/vigiechiro-pr-companion/issues/3455) [#3536](https://github.com/echonuit/vigiechiro-pr-companion/issues/3536) [#3672](https://github.com/echonuit/vigiechiro-pr-companion/issues/3672)
* **sites:** Mes sites relit sa donnée au retour, et suit la révision ([#3922](https://github.com/echonuit/vigiechiro-pr-companion/issues/3922)) ([ead54b6](https://github.com/echonuit/vigiechiro-pr-companion/commit/ead54b6d48364c14fb8765c4517f2e6a0b3b2918)), closes [#3644](https://github.com/echonuit/vigiechiro-pr-companion/issues/3644)
* **sites:** ne pas confondre un point homonyme posé ailleurs avec le nôtre ([#3735](https://github.com/echonuit/vigiechiro-pr-companion/issues/3735)) ([5b35316](https://github.com/echonuit/vigiechiro-pr-companion/commit/5b35316c64386202939f2d3d26ca4b8c7fd49e93))
* **sites:** un site déjà relié reçoit aussi les points de la plateforme ([#3697](https://github.com/echonuit/vigiechiro-pr-companion/issues/3697)) ([41879fb](https://github.com/echonuit/vigiechiro-pr-companion/commit/41879fbfe65c4652b05a793ba766441ecf160fed))
* **test:** chercher une source sans s’écrouler sur ce qui bouge ([#3658](https://github.com/echonuit/vigiechiro-pr-companion/issues/3658)) ([f3ceaca](https://github.com/echonuit/vigiechiro-pr-companion/commit/f3ceacaf13eee4a821a4d63245b011972b36fbb4))
* **test:** le garde de la fenêtre d'ouverture ne dépend plus de l'écran du runner ([#3804](https://github.com/echonuit/vigiechiro-pr-companion/issues/3804)) ([3ead779](https://github.com/echonuit/vigiechiro-pr-companion/commit/3ead779bf66ec2125da8e3697aa7e283d33133cc)), closes [#3622](https://github.com/echonuit/vigiechiro-pr-companion/issues/3622)
* **test:** mesurer l’accueil sans figer le Stage partagé ([#3638](https://github.com/echonuit/vigiechiro-pr-companion/issues/3638)) ([4eef036](https://github.com/echonuit/vigiechiro-pr-companion/commit/4eef03611d2e46740a8f5e6a7bd5f01fc02d7be9)), closes [#1940](https://github.com/echonuit/vigiechiro-pr-companion/issues/1940) [#1967](https://github.com/echonuit/vigiechiro-pr-companion/issues/1967) [#3452](https://github.com/echonuit/vigiechiro-pr-companion/issues/3452) [#1967](https://github.com/echonuit/vigiechiro-pr-companion/issues/1967) [#3452](https://github.com/echonuit/vigiechiro-pr-companion/issues/3452) [#1967](https://github.com/echonuit/vigiechiro-pr-companion/issues/1967)
* **verrou:** l'instant de l'occupant se lit en français ([#3677](https://github.com/echonuit/vigiechiro-pr-companion/issues/3677)) ([8dde369](https://github.com/echonuit/vigiechiro-pr-companion/commit/8dde369330192979aff1fc904527b01a8100dcda)), closes [#3640](https://github.com/echonuit/vigiechiro-pr-companion/issues/3640)
* **verrou:** la lecture évite l'octet verrouillé, pas seulement l'écriture ([#3714](https://github.com/echonuit/vigiechiro-pr-companion/issues/3714)) ([0d91686](https://github.com/echonuit/vigiechiro-pr-companion/commit/0d9168628f52fb4255e3b0dd89dcd262416351be)), closes [#3693](https://github.com/echonuit/vigiechiro-pr-companion/issues/3693)
* **verrou:** ne verrouiller qu'un octet, pour que le nom reste lisible ([#3705](https://github.com/echonuit/vigiechiro-pr-companion/issues/3705)) ([66dd848](https://github.com/echonuit/vigiechiro-pr-companion/commit/66dd848c64abe619c7dbc8833a874c43df427e1d)), closes [#3571](https://github.com/echonuit/vigiechiro-pr-companion/issues/3571) [#3693](https://github.com/echonuit/vigiechiro-pr-companion/issues/3693)


### Features

* **captures:** un aperçu pour le refus qui bloque le téléversement ([#3913](https://github.com/echonuit/vigiechiro-pr-companion/issues/3913)) ([816c679](https://github.com/echonuit/vigiechiro-pr-companion/commit/816c679aa2fc5972fa8f8826acbb075b52fd978d)), closes [#3854](https://github.com/echonuit/vigiechiro-pr-companion/issues/3854) [#2813](https://github.com/echonuit/vigiechiro-pr-companion/issues/2813)
* **ci:** programmer la suite de plateformes et en faire la condition du train ([#3770](https://github.com/echonuit/vigiechiro-pr-companion/issues/3770)) ([9528c5a](https://github.com/echonuit/vigiechiro-pr-companion/commit/9528c5ae6565a3d82bec19b995cfa57b4827dd66)), closes [#3754](https://github.com/echonuit/vigiechiro-pr-companion/issues/3754) [#3773](https://github.com/echonuit/vigiechiro-pr-companion/issues/3773)
* **ci:** une garde pour les inventaires que la CI tient sur elle-même ([#3810](https://github.com/echonuit/vigiechiro-pr-companion/issues/3810)) ([afb4529](https://github.com/echonuit/vigiechiro-pr-companion/commit/afb452900f3d95f291f1f767fa83580f28e72eb3)), closes [#3794](https://github.com/echonuit/vigiechiro-pr-companion/issues/3794) [#3771](https://github.com/echonuit/vigiechiro-pr-companion/issues/3771)
* **cli:** recuperer-carre rapatrie un carré depuis Vigie-Chiro ([#3901](https://github.com/echonuit/vigiechiro-pr-companion/issues/3901)) ([8f1e356](https://github.com/echonuit/vigiechiro-pr-companion/commit/8f1e356eae3f8ddcb063c89c33735ad26d97ae7b)), closes [#3806](https://github.com/echonuit/vigiechiro-pr-companion/issues/3806) [#3498](https://github.com/echonuit/vigiechiro-pr-companion/issues/3498)
* **cli:** rendre à l'utilisateur le dernier mot dans les deux sens ([#3818](https://github.com/echonuit/vigiechiro-pr-companion/issues/3818)) ([fc3133d](https://github.com/echonuit/vigiechiro-pr-companion/commit/fc3133de49ef2e2f696d444b2acae99f6ae07804)), closes [#3796](https://github.com/echonuit/vigiechiro-pr-companion/issues/3796) [#3796](https://github.com/echonuit/vigiechiro-pr-companion/issues/3796)
* **commun:** un fil d'Ariane élide des segments, il ne rogne pas des libellés ([#3868](https://github.com/echonuit/vigiechiro-pr-companion/issues/3868)) ([6e9f8c7](https://github.com/echonuit/vigiechiro-pr-companion/commit/6e9f8c79fd3612121e4674fde701a57bd61e2bfc)), closes [#3760](https://github.com/echonuit/vigiechiro-pr-companion/issues/3760) [#3798](https://github.com/echonuit/vigiechiro-pr-companion/issues/3798)
* **depot:** un refus définitif se réarme sur ce qui a levé sa cause ([#3943](https://github.com/echonuit/vigiechiro-pr-companion/issues/3943)) ([a28b27d](https://github.com/echonuit/vigiechiro-pr-companion/commit/a28b27d8d14f7414e6b041ecffdd3acdf129f2c3)), closes [#3687](https://github.com/echonuit/vigiechiro-pr-companion/issues/3687) [#3689](https://github.com/echonuit/vigiechiro-pr-companion/issues/3689) [#3687](https://github.com/echonuit/vigiechiro-pr-companion/issues/3687) [#3689](https://github.com/echonuit/vigiechiro-pr-companion/issues/3689)
* **doc:** la carte se monte là où une vraie carte se monte ([#3996](https://github.com/echonuit/vigiechiro-pr-companion/issues/3996)) ([00b9b1a](https://github.com/echonuit/vigiechiro-pr-companion/commit/00b9b1ab7de0e63f06be000246e236df6f548020)), closes [#3887](https://github.com/echonuit/vigiechiro-pr-companion/issues/3887)
* **doc:** le banc fabrique sa carte SD, en octets déterministes ([#3982](https://github.com/echonuit/vigiechiro-pr-companion/issues/3982)) ([8a081d4](https://github.com/echonuit/vigiechiro-pr-companion/commit/8a081d49e1d6153ee3699f5b1c999fd71c6ef139)), closes [#2191](https://github.com/echonuit/vigiechiro-pr-companion/issues/2191) [#3887](https://github.com/echonuit/vigiechiro-pr-companion/issues/3887)
* **doc:** le banc tourne le parcours d'importation ([#4005](https://github.com/echonuit/vigiechiro-pr-companion/issues/4005)) ([8c7ebfd](https://github.com/echonuit/vigiechiro-pr-companion/commit/8c7ebfd5777312944607bd0e3785cca32a975a9d)), closes [#3996](https://github.com/echonuit/vigiechiro-pr-companion/issues/3996) [#3887](https://github.com/echonuit/vigiechiro-pr-companion/issues/3887)
* **doc:** le geste vérifie son libellé avant de cliquer ([#3954](https://github.com/echonuit/vigiechiro-pr-companion/issues/3954)) ([e148c24](https://github.com/echonuit/vigiechiro-pr-companion/commit/e148c241a37032248b549074d1a4fb473e8ae70b)), closes [#2191](https://github.com/echonuit/vigiechiro-pr-companion/issues/2191) [#3887](https://github.com/echonuit/vigiechiro-pr-companion/issues/3887)
* **doc:** le montage coupe ce que le parcours n'occupe pas ([#3972](https://github.com/echonuit/vigiechiro-pr-companion/issues/3972)) ([ecf1153](https://github.com/echonuit/vigiechiro-pr-companion/commit/ecf11536f990a8bea0b066e742feba4d9394e6b1)), closes [#2191](https://github.com/echonuit/vigiechiro-pr-companion/issues/2191) [#3886](https://github.com/echonuit/vigiechiro-pr-companion/issues/3886) [#3887](https://github.com/echonuit/vigiechiro-pr-companion/issues/3887)
* **doc:** le premier parcours filmé, déclarer un carré de bout en bout ([#3968](https://github.com/echonuit/vigiechiro-pr-companion/issues/3968)) ([082612c](https://github.com/echonuit/vigiechiro-pr-companion/commit/082612c98f1cb970c16adaeaee24b54af7dfa9e9)), closes [#3887](https://github.com/echonuit/vigiechiro-pr-companion/issues/3887) [#2191](https://github.com/echonuit/vigiechiro-pr-companion/issues/2191) [#3667](https://github.com/echonuit/vigiechiro-pr-companion/issues/3667)
* **doc:** les deux parcours filmés paraissent dans la documentation ([#4009](https://github.com/echonuit/vigiechiro-pr-companion/issues/4009)) ([3fd00a8](https://github.com/echonuit/vigiechiro-pr-companion/commit/3fd00a841710257188c24eec3f3d5f97e50494d4)), closes [#3887](https://github.com/echonuit/vigiechiro-pr-companion/issues/3887) [#3887](https://github.com/echonuit/vigiechiro-pr-companion/issues/3887)
* **doc:** un banc qui filme le produit livré, et refuse de filmer le vide ([#3945](https://github.com/echonuit/vigiechiro-pr-companion/issues/3945)) ([e0302ad](https://github.com/echonuit/vigiechiro-pr-companion/commit/e0302ad1937b95e086672c99bbb0cd69e924cc2d)), closes [#3887](https://github.com/echonuit/vigiechiro-pr-companion/issues/3887) [#3885](https://github.com/echonuit/vigiechiro-pr-companion/issues/3885) [#3707](https://github.com/echonuit/vigiechiro-pr-companion/issues/3707) [#3883](https://github.com/echonuit/vigiechiro-pr-companion/issues/3883) [#3887](https://github.com/echonuit/vigiechiro-pr-companion/issues/3887)
* **doc:** un index qui dit quel film montre quoi, et ce qu'il ne prouve pas ([#3979](https://github.com/echonuit/vigiechiro-pr-companion/issues/3979)) ([bfe9127](https://github.com/echonuit/vigiechiro-pr-companion/commit/bfe9127fc7204883e208a072f3229e1398e13328)), closes [#3885](https://github.com/echonuit/vigiechiro-pr-companion/issues/3885) [#3887](https://github.com/echonuit/vigiechiro-pr-companion/issues/3887)
* **importation:** l'annonce de participation dit ce qu'il reste à faire ([#3936](https://github.com/echonuit/vigiechiro-pr-companion/issues/3936)) ([fd6b5d4](https://github.com/echonuit/vigiechiro-pr-companion/commit/fd6b5d4afa384e59f3ebf2091beb129f4ccbd06c)), closes [#3448](https://github.com/echonuit/vigiechiro-pr-companion/issues/3448) [#3473](https://github.com/echonuit/vigiechiro-pr-companion/issues/3473)
* **lot:** un aperçu montre le refus qui ne se reprend pas, et corrige ce qu'il révèle ([#3984](https://github.com/echonuit/vigiechiro-pr-companion/issues/3984)) ([fda41f7](https://github.com/echonuit/vigiechiro-pr-companion/commit/fda41f75b6a0a54cf3e6cee78f6a06670f56ec3d)), closes [#3959](https://github.com/echonuit/vigiechiro-pr-companion/issues/3959)
* **maj:** le conseil de mise a jour connait le canal winget ([#3636](https://github.com/echonuit/vigiechiro-pr-companion/issues/3636)) ([c8e64e5](https://github.com/echonuit/vigiechiro-pr-companion/commit/c8e64e5761dcce9f49273d9160173ac362cda471))
* **maj:** un aperçu montre l'annonce dans sa variante Windows ([#3909](https://github.com/echonuit/vigiechiro-pr-companion/issues/3909)) ([584b16e](https://github.com/echonuit/vigiechiro-pr-companion/commit/584b16e8c4ab0f0ea82dd551368d872d67af44c9)), closes [#3876](https://github.com/echonuit/vigiechiro-pr-companion/issues/3876) [#lienAnnonce](https://github.com/echonuit/vigiechiro-pr-companion/issues/lienAnnonce) [#3457](https://github.com/echonuit/vigiechiro-pr-companion/issues/3457) [#3876](https://github.com/echonuit/vigiechiro-pr-companion/issues/3876) [#3457](https://github.com/echonuit/vigiechiro-pr-companion/issues/3457) [#3457](https://github.com/echonuit/vigiechiro-pr-companion/issues/3457)
* **recette:** faire citer par le code les cas de recette qu'il couvre ([#3730](https://github.com/echonuit/vigiechiro-pr-companion/issues/3730)) ([ec5fe58](https://github.com/echonuit/vigiechiro-pr-companion/commit/ec5fe5829d7b53bf35c6af26439e195cf8e76728)), closes [#3728](https://github.com/echonuit/vigiechiro-pr-companion/issues/3728)
* **recette:** l'index dit par quel moyen chaque cas s'audite ([#3849](https://github.com/echonuit/vigiechiro-pr-companion/issues/3849)) ([c5f71f2](https://github.com/echonuit/vigiechiro-pr-companion/commit/c5f71f2e6efb3b6043dad3ba91730e63c39edba8)), closes [#3764](https://github.com/echonuit/vigiechiro-pr-companion/issues/3764) [#3835](https://github.com/echonuit/vigiechiro-pr-companion/issues/3835)
* **recette:** la séance consigne quand chaque cas se joue ([#3776](https://github.com/echonuit/vigiechiro-pr-companion/issues/3776)) ([497e8ab](https://github.com/echonuit/vigiechiro-pr-companion/commit/497e8ab18b92e40ce7da9f979c23cebd7b6f4315)), closes [#3774](https://github.com/echonuit/vigiechiro-pr-companion/issues/3774) [#3764](https://github.com/echonuit/vigiechiro-pr-companion/issues/3764)
* **recette:** le montage taille un clip par test, et l'index se lit par cas ([#3780](https://github.com/echonuit/vigiechiro-pr-companion/issues/3780)) ([aff7f44](https://github.com/echonuit/vigiechiro-pr-companion/commit/aff7f44bc0a29cddd17c030ab46dfbc95376a331)), closes [#3774](https://github.com/echonuit/vigiechiro-pr-companion/issues/3774)
* **recette:** réunir en une commande les cinq conditions d'un lancement filmé ([#3706](https://github.com/echonuit/vigiechiro-pr-companion/issues/3706)) ([3273c28](https://github.com/echonuit/vigiechiro-pr-companion/commit/3273c28e25b54623081048c0e2946ad73b3f29bd)), closes [#3696](https://github.com/echonuit/vigiechiro-pr-companion/issues/3696)
* **recette:** S1-26 se joue, pour qu'un humain le tranche en regardant ([#3792](https://github.com/echonuit/vigiechiro-pr-companion/issues/3792)) ([7735aab](https://github.com/echonuit/vigiechiro-pr-companion/commit/7735aab3bb9799f7c6990eaaba84c9a430dd7819)), closes [#3791](https://github.com/echonuit/vigiechiro-pr-companion/issues/3791)
* **recette:** S1-27 se joue, en câblant l'exécuteur de la production ([#3812](https://github.com/echonuit/vigiechiro-pr-companion/issues/3812)) ([d1d003e](https://github.com/echonuit/vigiechiro-pr-companion/commit/d1d003e6a7a7c5237ced5b34d0b44f9ee5d5b747)), closes [#3791](https://github.com/echonuit/vigiechiro-pr-companion/issues/3791) [#3791](https://github.com/echonuit/vigiechiro-pr-companion/issues/3791)
* **recette:** une planche de contact, dérivée du code et non d'une liste ([#3841](https://github.com/echonuit/vigiechiro-pr-companion/issues/3841)) ([a49bbe8](https://github.com/echonuit/vigiechiro-pr-companion/commit/a49bbe843e994ba5ab16e84d6d588c4e617f4594)), closes [#3728](https://github.com/echonuit/vigiechiro-pr-companion/issues/3728) [#3835](https://github.com/echonuit/vigiechiro-pr-companion/issues/3835)
* **securite:** rendre vérifiable la protection du jeton, ACL comprises ([#3816](https://github.com/echonuit/vigiechiro-pr-companion/issues/3816)) ([c7dc30d](https://github.com/echonuit/vigiechiro-pr-companion/commit/c7dc30da44d69fe1b321b639e1c04e2c9d2e1b5b)), closes [#3778](https://github.com/echonuit/vigiechiro-pr-companion/issues/3778) [#3714](https://github.com/echonuit/vigiechiro-pr-companion/issues/3714) [#3778](https://github.com/echonuit/vigiechiro-pr-companion/issues/3778)
* **sites:** dire quels points saisis avant connexion peuvent rejoindre une localité existante ([#3762](https://github.com/echonuit/vigiechiro-pr-companion/issues/3762)) ([8266dc7](https://github.com/echonuit/vigiechiro-pr-companion/commit/8266dc7c671530459bc8b3675128c483078675c7))
* **sites:** proposer de publier un point sur Vigie-Chiro dès sa création ([#3744](https://github.com/echonuit/vigiechiro-pr-companion/issues/3744)) ([513c549](https://github.com/echonuit/vigiechiro-pr-companion/commit/513c549836a736f45e01946bc22e930aaf0fedcc))
* **sites:** publier un point d'écoute sur Vigie-Chiro depuis la fiche du carré ([#3724](https://github.com/echonuit/vigiechiro-pr-companion/issues/3724)) ([57694ce](https://github.com/echonuit/vigiechiro-pr-companion/commit/57694ceb8d803e9432343126311f60925f2c5eff))
* **sites:** publier un point sur la plateforme sans effacer ceux des autres ([#3704](https://github.com/echonuit/vigiechiro-pr-companion/issues/3704)) ([07d8474](https://github.com/echonuit/vigiechiro-pr-companion/commit/07d847498eb62cfd35f3c810c046f94710747bce))
* **sites:** récupérer un carré de Vigie-Chiro depuis la fenêtre de déclaration ([#3832](https://github.com/echonuit/vigiechiro-pr-companion/issues/3832)) ([3d734c6](https://github.com/echonuit/vigiechiro-pr-companion/commit/3d734c651e6634ceec648ec9089276d94a848892)), closes [#3458](https://github.com/echonuit/vigiechiro-pr-companion/issues/3458) [#3463](https://github.com/echonuit/vigiechiro-pr-companion/issues/3463) [#3806](https://github.com/echonuit/vigiechiro-pr-companion/issues/3806) [#3458](https://github.com/echonuit/vigiechiro-pr-companion/issues/3458) [#2483](https://github.com/echonuit/vigiechiro-pr-companion/issues/2483)
* **sites:** retenir qu’un point a été publié sur la plateforme ([#3709](https://github.com/echonuit/vigiechiro-pr-companion/issues/3709)) ([cce2312](https://github.com/echonuit/vigiechiro-pr-companion/commit/cce2312d8e5fc542095944e68fb4504157bd1223)), closes [#2483](https://github.com/echonuit/vigiechiro-pr-companion/issues/2483)
* **sites:** vérifier depuis la modale si un carré existe déjà sur Vigie-Chiro ([#3787](https://github.com/echonuit/vigiechiro-pr-companion/issues/3787)) ([b8e0578](https://github.com/echonuit/vigiechiro-pr-companion/commit/b8e05789ae18fdc2ee5c7e2180b108cdb8df6bcb)), closes [#3458](https://github.com/echonuit/vigiechiro-pr-companion/issues/3458) [#3458](https://github.com/echonuit/vigiechiro-pr-companion/issues/3458)

# [2.185.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.184.0...v2.185.0) (2026-08-12)


### Bug Fixes

* **accueil:** les compteurs suivent la donnée, pas le changement de vue ([#3553](https://github.com/echonuit/vigiechiro-pr-companion/issues/3553)) ([3860662](https://github.com/echonuit/vigiechiro-pr-companion/commit/38606629bdf56e95bad310d25a40b36ae3150147)), closes [#1376](https://github.com/echonuit/vigiechiro-pr-companion/issues/1376) [#1376](https://github.com/echonuit/vigiechiro-pr-companion/issues/1376) [#3541](https://github.com/echonuit/vigiechiro-pr-companion/issues/3541) [RapportSynchro#aEcrit](https://github.com/RapportSynchro/issues/aEcrit) [#1376](https://github.com/echonuit/vigiechiro-pr-companion/issues/1376)
* **accueil:** ouvrir la fenêtre à la taille que l'accueil demande, bornée par l'écran ([#3607](https://github.com/echonuit/vigiechiro-pr-companion/issues/3607)) ([e34aaab](https://github.com/echonuit/vigiechiro-pr-companion/commit/e34aaabe98307abeb805df12e57209f6a8b3c25b)), closes [#2731](https://github.com/echonuit/vigiechiro-pr-companion/issues/2731) [#3452](https://github.com/echonuit/vigiechiro-pr-companion/issues/3452) [#3452](https://github.com/echonuit/vigiechiro-pr-companion/issues/3452)
* **analyse:** l'inventaire voit les observations qui arrivent pendant qu'on le regarde ([#3602](https://github.com/echonuit/vigiechiro-pr-companion/issues/3602)) ([befe986](https://github.com/echonuit/vigiechiro-pr-companion/commit/befe9860f8f48d27cbdf6d193f850654e863b673)), closes [#3591](https://github.com/echonuit/vigiechiro-pr-companion/issues/3591) [#230](https://github.com/echonuit/vigiechiro-pr-companion/issues/230) [#3592](https://github.com/echonuit/vigiechiro-pr-companion/issues/3592)
* **audit:** un constat chiffré par passage, au lieu d'un par fichier ([#3493](https://github.com/echonuit/vigiechiro-pr-companion/issues/3493)) ([1520e12](https://github.com/echonuit/vigiechiro-pr-companion/commit/1520e1252ddaf1dc2623155c6806a7e2bd2b2a1b)), closes [#3482](https://github.com/echonuit/vigiechiro-pr-companion/issues/3482)
* **captures:** un aperçu pose son temps écoulé, il ne le lit pas à l'horloge ([#3503](https://github.com/echonuit/vigiechiro-pr-companion/issues/3503)) ([f6c7924](https://github.com/echonuit/vigiechiro-pr-companion/commit/f6c7924b58d275a125bed5fadc28b551d21b9b7c)), closes [#814](https://github.com/echonuit/vigiechiro-pr-companion/issues/814) [#2733](https://github.com/echonuit/vigiechiro-pr-companion/issues/2733) [#146](https://github.com/echonuit/vigiechiro-pr-companion/issues/146) [#2733](https://github.com/echonuit/vigiechiro-pr-companion/issues/2733) [#3439](https://github.com/echonuit/vigiechiro-pr-companion/issues/3439)
* **captures:** un masque se dérive de la scène, il ne se recopie pas ([#3484](https://github.com/echonuit/vigiechiro-pr-companion/issues/3484)) ([337de06](https://github.com/echonuit/vigiechiro-pr-companion/commit/337de06b2b16ba32706e7fab8366c4e9e5364b9d))
* **ci:** un tag immobile masquait vingt et un mois de retard ([#3597](https://github.com/echonuit/vigiechiro-pr-companion/issues/3597)) ([b0647a9](https://github.com/echonuit/vigiechiro-pr-companion/commit/b0647a9cbe0ee3ac540020f7d0e27717962dc20a)), closes [#3382](https://github.com/echonuit/vigiechiro-pr-companion/issues/3382) [#3382](https://github.com/echonuit/vigiechiro-pr-companion/issues/3382)
* **cli:** la CLI réserve le dossier de travail avant d'écrire ([#3552](https://github.com/echonuit/vigiechiro-pr-companion/issues/3552)) ([5ad1d61](https://github.com/echonuit/vigiechiro-pr-companion/commit/5ad1d617f1a8534765049f73c0bfaf2e13ae53df)), closes [#3498](https://github.com/echonuit/vigiechiro-pr-companion/issues/3498) [#2731](https://github.com/echonuit/vigiechiro-pr-companion/issues/2731) [#3507](https://github.com/echonuit/vigiechiro-pr-companion/issues/3507)
* **cli:** la définition de « lecture seule » dit vrai, et son cliquet ne recopie rien ([#3623](https://github.com/echonuit/vigiechiro-pr-companion/issues/3623)) ([72ec6bd](https://github.com/echonuit/vigiechiro-pr-companion/commit/72ec6bd8629b47601c1a946645d0ae9d435b0234)), closes [#3575](https://github.com/echonuit/vigiechiro-pr-companion/issues/3575) [#3575](https://github.com/echonuit/vigiechiro-pr-companion/issues/3575)
* **cli:** rendre compte de ce qui échoue avant d'entrer dans la commande ([#3581](https://github.com/echonuit/vigiechiro-pr-companion/issues/3581)) ([ebd1abc](https://github.com/echonuit/vigiechiro-pr-companion/commit/ebd1abceb85b4ccb3f4c86154b9e469ab53e653f)), closes [#3570](https://github.com/echonuit/vigiechiro-pr-companion/issues/3570) [#3498](https://github.com/echonuit/vigiechiro-pr-companion/issues/3498)
* **cli:** restaurer distingue le manque du simple déplacement ([#3557](https://github.com/echonuit/vigiechiro-pr-companion/issues/3557)) ([d84078d](https://github.com/echonuit/vigiechiro-pr-companion/commit/d84078dc1c829b2561491fef38767376d3e84a27)), closes [#3500](https://github.com/echonuit/vigiechiro-pr-companion/issues/3500) [#2720](https://github.com/echonuit/vigiechiro-pr-companion/issues/2720)
* **commun:** Habillage pose le trio du chrome, pas ses deux tiers ([#3418](https://github.com/echonuit/vigiechiro-pr-companion/issues/3418)) ([181b84e](https://github.com/echonuit/vigiechiro-pr-companion/commit/181b84ec0f0ea01bbddb5bf310fc00f5b9e9d665)), closes [#3417](https://github.com/echonuit/vigiechiro-pr-companion/issues/3417) [#3417](https://github.com/echonuit/vigiechiro-pr-companion/issues/3417) [#3374](https://github.com/echonuit/vigiechiro-pr-companion/issues/3374)
* **commun:** habiller les dialogues du socle, panneau compris ([#3437](https://github.com/echonuit/vigiechiro-pr-companion/issues/3437)) ([72a5b1b](https://github.com/echonuit/vigiechiro-pr-companion/commit/72a5b1b39370679b2226ccc66b48ded69f05c776)), closes [#3374](https://github.com/echonuit/vigiechiro-pr-companion/issues/3374)
* **commun:** la restauration copie tout, vérifie tout, puis bascule ([#3532](https://github.com/echonuit/vigiechiro-pr-companion/issues/3532)) ([0fa7e94](https://github.com/echonuit/vigiechiro-pr-companion/commit/0fa7e941e76cbf0c1c6443629b9dcbbbad6c5588)), closes [#3514](https://github.com/echonuit/vigiechiro-pr-companion/issues/3514)
* **commun:** la restauration dégrade sa garantie plutôt que l'usage ([#3566](https://github.com/echonuit/vigiechiro-pr-companion/issues/3566)) ([242084a](https://github.com/echonuit/vigiechiro-pr-companion/commit/242084aa9b6281e9933832438d3ee9c45159d57d)), closes [#3563](https://github.com/echonuit/vigiechiro-pr-companion/issues/3563) [#3514](https://github.com/echonuit/vigiechiro-pr-companion/issues/3514) [#3514](https://github.com/echonuit/vigiechiro-pr-companion/issues/3514) [#3498](https://github.com/echonuit/vigiechiro-pr-companion/issues/3498)
* **commun:** le fichier d'amorçage s'écrit d'un seul coup ([#3523](https://github.com/echonuit/vigiechiro-pr-companion/issues/3523)) ([9b7ea5e](https://github.com/echonuit/vigiechiro-pr-companion/commit/9b7ea5e8f93442e3be21c24a96b78b6fd97f676c)), closes [#3507](https://github.com/echonuit/vigiechiro-pr-companion/issues/3507)
* **commun:** les cinq écritures structurelles que le lot 1 avait manquées ([#3578](https://github.com/echonuit/vigiechiro-pr-companion/issues/3578)) ([adb16ee](https://github.com/echonuit/vigiechiro-pr-companion/commit/adb16ee22770283fc809b4f3394916de5f9a28bc)), closes [#3537](https://github.com/echonuit/vigiechiro-pr-companion/issues/3537) [#3542](https://github.com/echonuit/vigiechiro-pr-companion/issues/3542) [#3537](https://github.com/echonuit/vigiechiro-pr-companion/issues/3537)
* **commun:** les octets se comptent en base 1000, à un seul endroit ([#3604](https://github.com/echonuit/vigiechiro-pr-companion/issues/3604)) ([917b024](https://github.com/echonuit/vigiechiro-pr-companion/commit/917b0249b3f550faf7b0ae53ba0e27e30cc477c4)), closes [#3573](https://github.com/echonuit/vigiechiro-pr-companion/issues/3573) [#3573](https://github.com/echonuit/vigiechiro-pr-companion/issues/3573)
* **commun:** retirer du menu la source des fiches espèces, doublon des Réglages ([#3433](https://github.com/echonuit/vigiechiro-pr-companion/issues/3433)) ([de5ccf3](https://github.com/echonuit/vigiechiro-pr-companion/commit/de5ccf3e6a2d7d1250b9c4f71fdbb483c36f172a)), closes [#930](https://github.com/echonuit/vigiechiro-pr-companion/issues/930) [#928](https://github.com/echonuit/vigiechiro-pr-companion/issues/928)
* **connexion:** accorder le libellé du compte rendu de synchronisation ([#3430](https://github.com/echonuit/vigiechiro-pr-companion/issues/3430)) ([7422851](https://github.com/echonuit/vigiechiro-pr-companion/commit/742285118e6967ee420579ab8dc33f2a06ff0790))
* **docs:** 2109 séquences, 33 tables, et une machine qui se nomme ([#3440](https://github.com/echonuit/vigiechiro-pr-companion/issues/3440)) ([bef01e5](https://github.com/echonuit/vigiechiro-pr-companion/commit/bef01e50d6a368c3113497647e3469567e2dd464)), closes [#504](https://github.com/echonuit/vigiechiro-pr-companion/issues/504) [#504](https://github.com/echonuit/vigiechiro-pr-companion/issues/504) [#104](https://github.com/echonuit/vigiechiro-pr-companion/issues/104) [#2749](https://github.com/echonuit/vigiechiro-pr-companion/issues/2749) [#2750](https://github.com/echonuit/vigiechiro-pr-companion/issues/2750) [#2749](https://github.com/echonuit/vigiechiro-pr-companion/issues/2749) [#2749](https://github.com/echonuit/vigiechiro-pr-companion/issues/2749)
* **docs:** ancrer le nombre de tests bats, qui annonçait 21 pour 89 ([#3426](https://github.com/echonuit/vigiechiro-pr-companion/issues/3426)) ([d4d5e83](https://github.com/echonuit/vigiechiro-pr-companion/commit/d4d5e83f62c269d8ad637e65cedf9fa765e45747)), closes [#2750](https://github.com/echonuit/vigiechiro-pr-companion/issues/2750) [#2749](https://github.com/echonuit/vigiechiro-pr-companion/issues/2749)
* **docs:** deux canaux annoncés vivants, et deux inventaires d'écrans qui divergeaient ([#3447](https://github.com/echonuit/vigiechiro-pr-companion/issues/3447)) ([d1695bc](https://github.com/echonuit/vigiechiro-pr-companion/commit/d1695bc44e2779f69d567541bb20a9e454b18831)), closes [#2753](https://github.com/echonuit/vigiechiro-pr-companion/issues/2753) [#2750](https://github.com/echonuit/vigiechiro-pr-companion/issues/2750) [#2749](https://github.com/echonuit/vigiechiro-pr-companion/issues/2749) [#3092](https://github.com/echonuit/vigiechiro-pr-companion/issues/3092) [#3426](https://github.com/echonuit/vigiechiro-pr-companion/issues/3426)
* **ihm:** déclarer chaque zone qui paraît, pas seulement la dernière ([#3619](https://github.com/echonuit/vigiechiro-pr-companion/issues/3619)) ([a4647bb](https://github.com/echonuit/vigiechiro-pr-companion/commit/a4647bbd30433fd1523fc86f44272d54579a865c)), closes [#3453](https://github.com/echonuit/vigiechiro-pr-companion/issues/3453)
* **importation:** l'état du nommage dit ce qu'on fait des fichiers, pas ce qu'ils sont ([#3533](https://github.com/echonuit/vigiechiro-pr-companion/issues/3533)) ([8052471](https://github.com/echonuit/vigiechiro-pr-companion/commit/8052471f0de36c7886fa73db7825998c898cb168))
* **importation:** le formulaire dit ce qu'il attend et montre ce qu'on lui a désigné ([#3528](https://github.com/echonuit/vigiechiro-pr-companion/issues/3528)) ([45557e4](https://github.com/echonuit/vigiechiro-pr-companion/commit/45557e49a9d95efee19ddbf2cf895be6625406d4)), closes [#1489](https://github.com/echonuit/vigiechiro-pr-companion/issues/1489)
* **importation:** lire « conserver les originaux » au moment de s'en servir ([#3485](https://github.com/echonuit/vigiechiro-pr-companion/issues/3485)) ([26de6b3](https://github.com/echonuit/vigiechiro-pr-companion/commit/26de6b3435766f09283052b656ed0c64e0edf252))
* **importation:** ne plus annoncer une participation que la plateforme a refusée ([#3477](https://github.com/echonuit/vigiechiro-pr-companion/issues/3477)) ([1798c22](https://github.com/echonuit/vigiechiro-pr-companion/commit/1798c225bf4e9ff551cb4850cf18bf7c7c5ad467))
* **importation:** suivre ce qui paraît, au lieu de remonter en haut ([#3549](https://github.com/echonuit/vigiechiro-pr-companion/issues/3549)) ([37fe06c](https://github.com/echonuit/vigiechiro-pr-companion/commit/37fe06c61e3b482df4ccce17802866a9576ae12b))
* **multisite:** Carte & passages voit ce qui arrive pendant qu'on le regarde ([#3613](https://github.com/echonuit/vigiechiro-pr-companion/issues/3613)) ([338a3f6](https://github.com/echonuit/vigiechiro-pr-companion/commit/338a3f6789d55a3cb2e403d21f0687df33482b06)), closes [#2757](https://github.com/echonuit/vigiechiro-pr-companion/issues/2757) [#3599](https://github.com/echonuit/vigiechiro-pr-companion/issues/3599) [#3606](https://github.com/echonuit/vigiechiro-pr-companion/issues/3606) [#3591](https://github.com/echonuit/vigiechiro-pr-companion/issues/3591) [#3592](https://github.com/echonuit/vigiechiro-pr-companion/issues/3592) [#3599](https://github.com/echonuit/vigiechiro-pr-companion/issues/3599)
* **passage:** dire le renommage réussi avant l'échec de l'envoi ([#3480](https://github.com/echonuit/vigiechiro-pr-companion/issues/3480)) ([54099dd](https://github.com/echonuit/vigiechiro-pr-companion/commit/54099dd81a0e5d1e269acbca6d35af8860968c6f)), closes [#1885](https://github.com/echonuit/vigiechiro-pr-companion/issues/1885)
* **passage:** épingler l'avertissement irréversible, comme le reste du pied ([#3555](https://github.com/echonuit/vigiechiro-pr-companion/issues/3555)) ([542ae09](https://github.com/echonuit/vigiechiro-pr-companion/commit/542ae09c845950645012e1699098324f5eda8062)), closes [#2496](https://github.com/echonuit/vigiechiro-pr-companion/issues/2496) [#1494](https://github.com/echonuit/vigiechiro-pr-companion/issues/1494)
* **passage:** le fuseau d'une nuit est celui du site, pas du poste ([#3434](https://github.com/echonuit/vigiechiro-pr-companion/issues/3434)) ([4a65aa0](https://github.com/echonuit/vigiechiro-pr-companion/commit/4a65aa0a6fa2df96dd2eb4d72ad53c2b1dab6f83)), closes [#3406](https://github.com/echonuit/vigiechiro-pr-companion/issues/3406) [#1860](https://github.com/echonuit/vigiechiro-pr-companion/issues/1860)
* **passage:** une nuit sans bornes est refusée au dépôt, pas amputée ([#3475](https://github.com/echonuit/vigiechiro-pr-companion/issues/3475)) ([aba3890](https://github.com/echonuit/vigiechiro-pr-companion/commit/aba389086c480ff94391502496151179c43c0ca4))
* **saison:** le solde voit une nuit qui arrive pendant qu'on le regarde ([#3596](https://github.com/echonuit/vigiechiro-pr-companion/issues/3596)) ([c36a16f](https://github.com/echonuit/vigiechiro-pr-companion/commit/c36a16f9da49524168564cf828ce4ae857cd7d21)), closes [#1376](https://github.com/echonuit/vigiechiro-pr-companion/issues/1376) [#230](https://github.com/echonuit/vigiechiro-pr-companion/issues/230) [#3591](https://github.com/echonuit/vigiechiro-pr-companion/issues/3591)
* **sauvegarde:** refuser quand la place manque, et ne nommer qu'une fois complète ([#3589](https://github.com/echonuit/vigiechiro-pr-companion/issues/3589)) ([00283d7](https://github.com/echonuit/vigiechiro-pr-companion/commit/00283d71a860b53ef3d48bb1d457245e920f6ab0)), closes [#3574](https://github.com/echonuit/vigiechiro-pr-companion/issues/3574) [#3572](https://github.com/echonuit/vigiechiro-pr-companion/issues/3572) [#3572](https://github.com/echonuit/vigiechiro-pr-companion/issues/3572)
* **sites:** « Voir sur la carte » empile le contexte au lieu de l'écraser ([#3587](https://github.com/echonuit/vigiechiro-pr-companion/issues/3587)) ([8d4e9d2](https://github.com/echonuit/vigiechiro-pr-companion/commit/8d4e9d26435af8b2acadd3af7355dbb7e8cf39cd)), closes [#3582](https://github.com/echonuit/vigiechiro-pr-companion/issues/3582)
* **sites:** la fiche d'un carré voit les points qu'une synchro y rapatrie ([#3628](https://github.com/echonuit/vigiechiro-pr-companion/issues/3628)) ([7eb6a42](https://github.com/echonuit/vigiechiro-pr-companion/commit/7eb6a422cd8d6bbd2258d0d6c3e51177d03aade2)), closes [#3591](https://github.com/echonuit/vigiechiro-pr-companion/issues/3591) [#3592](https://github.com/echonuit/vigiechiro-pr-companion/issues/3592) [#3599](https://github.com/echonuit/vigiechiro-pr-companion/issues/3599) [#1738](https://github.com/echonuit/vigiechiro-pr-companion/issues/1738) [#3593](https://github.com/echonuit/vigiechiro-pr-companion/issues/3593)
* **sites:** la modale dit que renommer un site relié reste local ([#3554](https://github.com/echonuit/vigiechiro-pr-companion/issues/3554)) ([9d2ece3](https://github.com/echonuit/vigiechiro-pr-companion/commit/9d2ece342428528423a5ec21ae308721b0b4b6b1))
* **sites:** le menu rejoint son tableau, et l'alerte de proximité dit sa règle ([#3556](https://github.com/echonuit/vigiechiro-pr-companion/issues/3556)) ([0e588d4](https://github.com/echonuit/vigiechiro-pr-companion/commit/0e588d4788296b71a999a521e5984a8877171282)), closes [#2221](https://github.com/echonuit/vigiechiro-pr-companion/issues/2221)
* **test:** mesurer l'accueil dans la fenêtre d'ouverture, pas dans celle du test précédent ([#3625](https://github.com/echonuit/vigiechiro-pr-companion/issues/3625)) ([0d27d14](https://github.com/echonuit/vigiechiro-pr-companion/commit/0d27d14bb45504e271205adf8f05990edf619a99))
* **verrou:** ne plus promettre un nom d'occupant qu'on n'a pas ([#3586](https://github.com/echonuit/vigiechiro-pr-companion/issues/3586)) ([d3805b9](https://github.com/echonuit/vigiechiro-pr-companion/commit/d3805b9f31f88f065710d495b90974e21255c67c)), closes [#3498](https://github.com/echonuit/vigiechiro-pr-companion/issues/3498) [#3571](https://github.com/echonuit/vigiechiro-pr-companion/issues/3571)
* **winget:** exiger un jeton utilisable, pas seulement present ([#3598](https://github.com/echonuit/vigiechiro-pr-companion/issues/3598)) ([4e2d5cc](https://github.com/echonuit/vigiechiro-pr-companion/commit/4e2d5cc75dcf53303716f3010b7dc1de71fb0693)), closes [#3382](https://github.com/echonuit/vigiechiro-pr-companion/issues/3382) [#3382](https://github.com/echonuit/vigiechiro-pr-companion/issues/3382) [#3596](https://github.com/echonuit/vigiechiro-pr-companion/issues/3596) [#1376](https://github.com/echonuit/vigiechiro-pr-companion/issues/1376) [#230](https://github.com/echonuit/vigiechiro-pr-companion/issues/230) [#3591](https://github.com/echonuit/vigiechiro-pr-companion/issues/3591)
* **winget:** la sonde rapporte la reponse de l'API, au lieu de l'avaler ([#3601](https://github.com/echonuit/vigiechiro-pr-companion/issues/3601)) ([c4c5ccd](https://github.com/echonuit/vigiechiro-pr-companion/commit/c4c5ccd4e8486133cdb620305ce9c4fc77cd768f))
* **winget:** nommer la politique d'entreprise qui refuse le jeton ([#3603](https://github.com/echonuit/vigiechiro-pr-companion/issues/3603)) ([53e0c5a](https://github.com/echonuit/vigiechiro-pr-companion/commit/53e0c5aca758790c54b2160e00c86a4a78246430))
* **winget:** rougir quand le secret de soumission manque ([#3594](https://github.com/echonuit/vigiechiro-pr-companion/issues/3594)) ([04bfac9](https://github.com/echonuit/vigiechiro-pr-companion/commit/04bfac9eed59aa5bb2b5d1b5b5d43e5f76f64836))


### Features

* **audit:** retirer les dossiers de session qu'aucun passage ne réclame ([#3504](https://github.com/echonuit/vigiechiro-pr-companion/issues/3504)) ([c91bba3](https://github.com/echonuit/vigiechiro-pr-companion/commit/c91bba3304c5163805ce6b5ce7ac2c9d66f52617))
* **ci:** ouvrir chaque emballage de distribution et le lancer ([#3629](https://github.com/echonuit/vigiechiro-pr-companion/issues/3629)) ([89cee0c](https://github.com/echonuit/vigiechiro-pr-companion/commit/89cee0c659555275e93b12b8b5ae3bc88cf8e7c5)), closes [#2299](https://github.com/echonuit/vigiechiro-pr-companion/issues/2299)
* **cli:** modifier-site, supprimer-site et modifier-point ([#3444](https://github.com/echonuit/vigiechiro-pr-companion/issues/3444)) ([96eccf8](https://github.com/echonuit/vigiechiro-pr-companion/commit/96eccf8228ddec367be8b0d80a288c976e68e1c1)), closes [#2294](https://github.com/echonuit/vigiechiro-pr-companion/issues/2294)
* **commun:** le fuseau d'un site se dérive de sa commune ([#3487](https://github.com/echonuit/vigiechiro-pr-companion/issues/3487)) ([aace4a5](https://github.com/echonuit/vigiechiro-pr-companion/commit/aace4a500fbed1f6fc80bda46cfec48ee1377976))
* **commun:** toutes les écritures structurelles annoncent leur mutation ([#3562](https://github.com/echonuit/vigiechiro-pr-companion/issues/3562)) ([593ef3b](https://github.com/echonuit/vigiechiro-pr-companion/commit/593ef3b5333464ebdc99ea2cd283f421ef897520)), closes [#3542](https://github.com/echonuit/vigiechiro-pr-companion/issues/3542) [#1376](https://github.com/echonuit/vigiechiro-pr-companion/issues/1376) [RapportSynchro#aEcrit](https://github.com/RapportSynchro/issues/aEcrit) [AccueilViewModel#relire](https://github.com/AccueilViewModel/issues/relire) [#3542](https://github.com/echonuit/vigiechiro-pr-companion/issues/3542) [ExecuteurTacheAsynchrone#surFilJavaFx](https://github.com/ExecuteurTacheAsynchrone/issues/surFilJavaFx)
* **commun:** un port donne la commune d'un point, donc son fuseau ([#3489](https://github.com/echonuit/vigiechiro-pr-companion/issues/3489)) ([5f7b1da](https://github.com/echonuit/vigiechiro-pr-companion/commit/5f7b1da9a3157268d47f130404a31594b2fef609))
* **commun:** un signal observable des mutations structurelles ([#3550](https://github.com/echonuit/vigiechiro-pr-companion/issues/3550)) ([cc3cf69](https://github.com/echonuit/vigiechiro-pr-companion/commit/cc3cf690a4d66ab107bd4d985e14180372a8a9df)), closes [#1376](https://github.com/echonuit/vigiechiro-pr-companion/issues/1376) [#3541](https://github.com/echonuit/vigiechiro-pr-companion/issues/3541)
* **importation:** n'importer que la série du journal, et dire ce qu'on écarte ([#3579](https://github.com/echonuit/vigiechiro-pr-companion/issues/3579)) ([2dc750f](https://github.com/echonuit/vigiechiro-pr-companion/commit/2dc750f15e65c3aecc61ffc84eb9b92a5566ce67)), closes [#1493](https://github.com/echonuit/vigiechiro-pr-companion/issues/1493) [#107](https://github.com/echonuit/vigiechiro-pr-companion/issues/107)
* **importation:** un préfixe discordant bloque l'import, au lieu d'avertir ([#3568](https://github.com/echonuit/vigiechiro-pr-companion/issues/3568)) ([e344db6](https://github.com/echonuit/vigiechiro-pr-companion/commit/e344db68357591216dc7d8f11a5ec9215c3c7a12))
* **passage:** corriger le point d'écoute depuis « Modifier le passage » ([#3564](https://github.com/echonuit/vigiechiro-pr-companion/issues/3564)) ([d41dae6](https://github.com/echonuit/vigiechiro-pr-companion/commit/d41dae6855b0975b3f29924b6c45b658a522464a)), closes [#3389](https://github.com/echonuit/vigiechiro-pr-companion/issues/3389) [#1495](https://github.com/echonuit/vigiechiro-pr-companion/issues/1495)
* **passage:** une nuit se dépose et se relit dans le fuseau de son site ([#3494](https://github.com/echonuit/vigiechiro-pr-companion/issues/3494)) ([7a94639](https://github.com/echonuit/vigiechiro-pr-companion/commit/7a94639e97e40b6bc808dfe1fc700c48bc83fe73)), closes [#1860](https://github.com/echonuit/vigiechiro-pr-companion/issues/1860)

# [2.184.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.183.0...v2.184.0) (2026-08-06)


### Bug Fixes

* **audit:** un carré d'outre-mer ne diverge plus contre lui-même ([#3303](https://github.com/echonuit/vigiechiro-pr-companion/issues/3303)) ([a5d02a9](https://github.com/echonuit/vigiechiro-pr-companion/commit/a5d02a9f7e3362ed9263d9412af860e1e23ce1ab)), closes [#3257](https://github.com/echonuit/vigiechiro-pr-companion/issues/3257)
* **captures:** épingler la langue et le fuseau du rendu des aperçus ([#3405](https://github.com/echonuit/vigiechiro-pr-companion/issues/3405)) ([1f283ad](https://github.com/echonuit/vigiechiro-pr-companion/commit/1f283ad5cca06a23039d9609e2251c6603ffd07c))
* **captures:** que le garde voie un manque dans la PR, et non une fois main rouge ([#3309](https://github.com/echonuit/vigiechiro-pr-companion/issues/3309)) ([8cb6b41](https://github.com/echonuit/vigiechiro-pr-companion/commit/8cb6b41359c96203bf834a3dd8e47bdc59237a31)), closes [#3119](https://github.com/echonuit/vigiechiro-pr-companion/issues/3119)
* **ci:** la répétition à blanc peut enfin vérifier ce qu'elle annonce ([#3347](https://github.com/echonuit/vigiechiro-pr-companion/issues/3347)) ([540b897](https://github.com/echonuit/vigiechiro-pr-companion/commit/540b89705d1ae592cf1b74beed1b8cffa1c2a143)), closes [#3252](https://github.com/echonuit/vigiechiro-pr-companion/issues/3252) [#2739](https://github.com/echonuit/vigiechiro-pr-companion/issues/2739)
* **ci:** la republication de la doc suit de nouveau les captures ([#3282](https://github.com/echonuit/vigiechiro-pr-companion/issues/3282)) ([fc65fef](https://github.com/echonuit/vigiechiro-pr-companion/commit/fc65fef8043d7390a343b0b91234078076cb1f32)), closes [#1439](https://github.com/echonuit/vigiechiro-pr-companion/issues/1439) [#1439](https://github.com/echonuit/vigiechiro-pr-companion/issues/1439) [#3279](https://github.com/echonuit/vigiechiro-pr-companion/issues/3279)
* **ci:** réparer la publication des aperçus, cassée depuis [#3359](https://github.com/echonuit/vigiechiro-pr-companion/issues/3359) ([#3370](https://github.com/echonuit/vigiechiro-pr-companion/issues/3370)) ([3c4741b](https://github.com/echonuit/vigiechiro-pr-companion/commit/3c4741b6bb8fb0528a3b15351b0d716922680860))
* **cli:** le point se compare, parce que la sortie le distingue ([#3383](https://github.com/echonuit/vigiechiro-pr-companion/issues/3383)) ([a9df2e1](https://github.com/echonuit/vigiechiro-pr-companion/commit/a9df2e10f4e1a3f04a9b732793df881495d4e9a7)), closes [#3350](https://github.com/echonuit/vigiechiro-pr-companion/issues/3350)
* **cli:** ne plus viser un Provider avec une lambda, qu'ecj refuse ([#3367](https://github.com/echonuit/vigiechiro-pr-companion/issues/3367)) ([1b20fec](https://github.com/echonuit/vigiechiro-pr-companion/commit/1b20fec5c3995b4d38dc3bfe15804e62f5747698))
* **cloture:** ce que les 10 passes des suites de [#3092](https://github.com/echonuit/vigiechiro-pr-companion/issues/3092) ont trouvé ([#3339](https://github.com/echonuit/vigiechiro-pr-companion/issues/3339)) ([6be1353](https://github.com/echonuit/vigiechiro-pr-companion/commit/6be135379a1f43bf2fbd1a10e2810853e41b178b)), closes [#3095](https://github.com/echonuit/vigiechiro-pr-companion/issues/3095) [#2225](https://github.com/echonuit/vigiechiro-pr-companion/issues/2225) [#2989](https://github.com/echonuit/vigiechiro-pr-companion/issues/2989) [#3170](https://github.com/echonuit/vigiechiro-pr-companion/issues/3170) [#3337](https://github.com/echonuit/vigiechiro-pr-companion/issues/3337) [#3103](https://github.com/echonuit/vigiechiro-pr-companion/issues/3103) [#3151](https://github.com/echonuit/vigiechiro-pr-companion/issues/3151) [#3336](https://github.com/echonuit/vigiechiro-pr-companion/issues/3336) [#3330](https://github.com/echonuit/vigiechiro-pr-companion/issues/3330) [#3082](https://github.com/echonuit/vigiechiro-pr-companion/issues/3082) [#3082](https://github.com/echonuit/vigiechiro-pr-companion/issues/3082) [#3082](https://github.com/echonuit/vigiechiro-pr-companion/issues/3082) [#3082](https://github.com/echonuit/vigiechiro-pr-companion/issues/3082)
* **cloture:** ce que les passes du lot 3 ont trouvé ([#3295](https://github.com/echonuit/vigiechiro-pr-companion/issues/3295)) ([a1159ce](https://github.com/echonuit/vigiechiro-pr-companion/commit/a1159ce9f84ea687aecf048305d5454cd9e23522)), closes [#3254](https://github.com/echonuit/vigiechiro-pr-companion/issues/3254) [#3263](https://github.com/echonuit/vigiechiro-pr-companion/issues/3263) [#2744](https://github.com/echonuit/vigiechiro-pr-companion/issues/2744) [#2723](https://github.com/echonuit/vigiechiro-pr-companion/issues/2723)
* **commun:** une fenêtre porte son habillage, ou elle ne montre pas le produit ([#3375](https://github.com/echonuit/vigiechiro-pr-companion/issues/3375)) ([e3cda7a](https://github.com/echonuit/vigiechiro-pr-companion/commit/e3cda7a97e868d98e39ac69dbb43373500c6065c)), closes [#3361](https://github.com/echonuit/vigiechiro-pr-companion/issues/3361) [#3374](https://github.com/echonuit/vigiechiro-pr-companion/issues/3374) [#3364](https://github.com/echonuit/vigiechiro-pr-companion/issues/3364) [#3374](https://github.com/echonuit/vigiechiro-pr-companion/issues/3374)
* **commun:** une invite de recherche coupée net, et le garde qui ne la voyait pas ([#3286](https://github.com/echonuit/vigiechiro-pr-companion/issues/3286)) ([f644929](https://github.com/echonuit/vigiechiro-pr-companion/commit/f644929884a4684a179e6e1fb4f020bc8108f4ed))
* **deps:** nommer le vrai coupable de l'échéance Unsafe, et rendre analyze lisible ([#3369](https://github.com/echonuit/vigiechiro-pr-companion/issues/3369)) ([c30e9ad](https://github.com/echonuit/vigiechiro-pr-companion/commit/c30e9ad00701dddfc3bc05906f38261a4a3b88bf)), closes [#2740](https://github.com/echonuit/vigiechiro-pr-companion/issues/2740)
* **docs:** valider les ancres, et réparer les quatre qui étaient cassées ([#3360](https://github.com/echonuit/vigiechiro-pr-companion/issues/3360)) ([91c7b5e](https://github.com/echonuit/vigiechiro-pr-companion/commit/91c7b5e55a81c8940d6c363ceb501c3740e033df)), closes [#192](https://github.com/echonuit/vigiechiro-pr-companion/issues/192)
* **graphify:** un script deja dans le graphe ne se dedouble plus ([#3290](https://github.com/echonuit/vigiechiro-pr-companion/issues/3290)) ([3855c1b](https://github.com/echonuit/vigiechiro-pr-companion/commit/3855c1b1f035dea237c3f9ea17098f1a71bd605e)), closes [#3288](https://github.com/echonuit/vigiechiro-pr-companion/issues/3288)
* **ihm:** ce que l'application affiche tient dans la police embarquée ([#3399](https://github.com/echonuit/vigiechiro-pr-companion/issues/3399)) ([1ba019e](https://github.com/echonuit/vigiechiro-pr-companion/commit/1ba019e5d08a828e1a752e30e23bc644079a37bc)), closes [#3389](https://github.com/echonuit/vigiechiro-pr-companion/issues/3389)
* **release:** le train ne commente plus les issues, ce que sa taille interdit ([#3416](https://github.com/echonuit/vigiechiro-pr-companion/issues/3416)) ([158d068](https://github.com/echonuit/vigiechiro-pr-companion/commit/158d0685ddd0e01be8b3e1f900e1bbc57ae3a8f5))
* **saison:** montrer le nom du carré, par lequel la recherche trouve déjà ([#3297](https://github.com/echonuit/vigiechiro-pr-companion/issues/3297)) ([7673772](https://github.com/echonuit/vigiechiro-pr-companion/commit/7673772c2e0ebdd8a735f4e4e1d331420ee06992)), closes [#3219](https://github.com/echonuit/vigiechiro-pr-companion/issues/3219) [#3219](https://github.com/echonuit/vigiechiro-pr-companion/issues/3219) [#3289](https://github.com/echonuit/vigiechiro-pr-companion/issues/3289)


### Features

* **audio:** ce qui se cherche doit pouvoir se balayer ([#3393](https://github.com/echonuit/vigiechiro-pr-companion/issues/3393)) ([505fc00](https://github.com/echonuit/vigiechiro-pr-companion/commit/505fc00fd72bd92cb9296baefc543728d7dca8c1)), closes [#3300](https://github.com/echonuit/vigiechiro-pr-companion/issues/3300) [#3348](https://github.com/echonuit/vigiechiro-pr-companion/issues/3348) [#3375](https://github.com/echonuit/vigiechiro-pr-companion/issues/3375) [#3391](https://github.com/echonuit/vigiechiro-pr-companion/issues/3391) [#3389](https://github.com/echonuit/vigiechiro-pr-companion/issues/3389) [#3391](https://github.com/echonuit/vigiechiro-pr-companion/issues/3391) [#3389](https://github.com/echonuit/vigiechiro-pr-companion/issues/3389)
* **audio:** la certitude se filtre aussi à l'écran ([#3349](https://github.com/echonuit/vigiechiro-pr-companion/issues/3349)) ([241d4b4](https://github.com/echonuit/vigiechiro-pr-companion/commit/241d4b42885cca4aa20498ab0a3e784db46e6313)), closes [#3092](https://github.com/echonuit/vigiechiro-pr-companion/issues/3092) [#3105](https://github.com/echonuit/vigiechiro-pr-companion/issues/3105) [#3336](https://github.com/echonuit/vigiechiro-pr-companion/issues/3336)
* **captures:** montrer la valeur hors jeu, sans toucher à aucune graine ([#3299](https://github.com/echonuit/vigiechiro-pr-companion/issues/3299)) ([8dbcbd8](https://github.com/echonuit/vigiechiro-pr-companion/commit/8dbcbd8b1092f1b374d8b47984c82e27ff1ce0fb)), closes [#3092](https://github.com/echonuit/vigiechiro-pr-companion/issues/3092)
* **ci:** garder les renvois entre workflows contre un libellé mort ([#3355](https://github.com/echonuit/vigiechiro-pr-companion/issues/3355)) ([3cd311f](https://github.com/echonuit/vigiechiro-pr-companion/commit/3cd311f3e8f6ed8079e875976993ff8e0558bb35)), closes [#1439](https://github.com/echonuit/vigiechiro-pr-companion/issues/1439) [#3335](https://github.com/echonuit/vigiechiro-pr-companion/issues/3335)
* **ci:** la publication part à heure fixe, plus à chaque fusion ([#3283](https://github.com/echonuit/vigiechiro-pr-companion/issues/3283)) ([b6330da](https://github.com/echonuit/vigiechiro-pr-companion/commit/b6330da37e00817f27328d15682c35f37f57f058)), closes [#1363](https://github.com/echonuit/vigiechiro-pr-companion/issues/1363) [#2744](https://github.com/echonuit/vigiechiro-pr-companion/issues/2744) [#2191](https://github.com/echonuit/vigiechiro-pr-companion/issues/2191) [#2744](https://github.com/echonuit/vigiechiro-pr-companion/issues/2744)
* **ci:** une garde maintient le moindre privilège des workflows ([#3305](https://github.com/echonuit/vigiechiro-pr-companion/issues/3305)) ([4a2c749](https://github.com/echonuit/vigiechiro-pr-companion/commit/4a2c74959869216c07ffbb40c49cf99798c7d21a)), closes [#2739](https://github.com/echonuit/vigiechiro-pr-companion/issues/2739) [#2742](https://github.com/echonuit/vigiechiro-pr-companion/issues/2742) [#2739](https://github.com/echonuit/vigiechiro-pr-companion/issues/2739) [#3294](https://github.com/echonuit/vigiechiro-pr-companion/issues/3294) [#3294](https://github.com/echonuit/vigiechiro-pr-companion/issues/3294)
* **cli:** les deux inventaires d'espèces, posables sans ouvrir l'application ([#3323](https://github.com/echonuit/vigiechiro-pr-companion/issues/3323)) ([b7f08bb](https://github.com/echonuit/vigiechiro-pr-companion/commit/b7f08bb63748926e2e3ceced0d9579a5c96a3017)), closes [#3269](https://github.com/echonuit/vigiechiro-pr-companion/issues/3269) [#3059](https://github.com/echonuit/vigiechiro-pr-companion/issues/3059) [#3269](https://github.com/echonuit/vigiechiro-pr-companion/issues/3269)
* **cli:** les trois derniers filtres de lister-passages, en étendant la lecture ([#3320](https://github.com/echonuit/vigiechiro-pr-companion/issues/3320)) ([d3fa103](https://github.com/echonuit/vigiechiro-pr-companion/commit/d3fa103b979e1aa34fb40789330e9b530bb0fb6c))
* **cli:** quatre filtres pour lister-passages, sur le prédicat de l'écran ([#3316](https://github.com/echonuit/vigiechiro-pr-companion/issues/3316)) ([8931490](https://github.com/echonuit/vigiechiro-pr-companion/commit/89314908c27d96b36e7f610b0682ba7a6a7fe5a5))
* **ihm:** embarquer la monospace, alias que chaque système résout autrement ([#3413](https://github.com/echonuit/vigiechiro-pr-companion/issues/3413)) ([333ada9](https://github.com/echonuit/vigiechiro-pr-companion/commit/333ada9e68dc173d6523e7399c8234f057b61d50)), closes [#3412](https://github.com/echonuit/vigiechiro-pr-companion/issues/3412)
* **ihm:** embarquer la typographie, que le produit empruntait à la machine ([#3364](https://github.com/echonuit/vigiechiro-pr-companion/issues/3364)) ([a017d7d](https://github.com/echonuit/vigiechiro-pr-companion/commit/a017d7d838f2ac33aecb85f2aefeb6a9f73220f9)), closes [#3361](https://github.com/echonuit/vigiechiro-pr-companion/issues/3361)
* **ihm:** le nom du carré a sa colonne sur les deux écrans qui le cherchent ([#3310](https://github.com/echonuit/vigiechiro-pr-companion/issues/3310)) ([d094c8c](https://github.com/echonuit/vigiechiro-pr-companion/commit/d094c8c9b8590a4eeea363e98b00779da80f644e)), closes [#3289](https://github.com/echonuit/vigiechiro-pr-companion/issues/3289) [#919](https://github.com/echonuit/vigiechiro-pr-companion/issues/919)
* **saison:** offrir et montrer la commune, comme les quatre autres écrans ([#3317](https://github.com/echonuit/vigiechiro-pr-companion/issues/3317)) ([992c270](https://github.com/echonuit/vigiechiro-pr-companion/commit/992c270ad4976db2161c012d0f2cdfc57546d13e)), closes [#3151](https://github.com/echonuit/vigiechiro-pr-companion/issues/3151) [#3289](https://github.com/echonuit/vigiechiro-pr-companion/issues/3289) [#3289](https://github.com/echonuit/vigiechiro-pr-companion/issues/3289) [#3280](https://github.com/echonuit/vigiechiro-pr-companion/issues/3280)

# [2.184.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.183.0...v2.184.0) (2026-08-06)


### Bug Fixes

* **audit:** un carré d'outre-mer ne diverge plus contre lui-même ([#3303](https://github.com/echonuit/vigiechiro-pr-companion/issues/3303)) ([a5d02a9](https://github.com/echonuit/vigiechiro-pr-companion/commit/a5d02a9f7e3362ed9263d9412af860e1e23ce1ab)), closes [#3257](https://github.com/echonuit/vigiechiro-pr-companion/issues/3257)
* **captures:** épingler la langue et le fuseau du rendu des aperçus ([#3405](https://github.com/echonuit/vigiechiro-pr-companion/issues/3405)) ([1f283ad](https://github.com/echonuit/vigiechiro-pr-companion/commit/1f283ad5cca06a23039d9609e2251c6603ffd07c))
* **captures:** que le garde voie un manque dans la PR, et non une fois main rouge ([#3309](https://github.com/echonuit/vigiechiro-pr-companion/issues/3309)) ([8cb6b41](https://github.com/echonuit/vigiechiro-pr-companion/commit/8cb6b41359c96203bf834a3dd8e47bdc59237a31)), closes [#3119](https://github.com/echonuit/vigiechiro-pr-companion/issues/3119)
* **ci:** la répétition à blanc peut enfin vérifier ce qu'elle annonce ([#3347](https://github.com/echonuit/vigiechiro-pr-companion/issues/3347)) ([540b897](https://github.com/echonuit/vigiechiro-pr-companion/commit/540b89705d1ae592cf1b74beed1b8cffa1c2a143)), closes [#3252](https://github.com/echonuit/vigiechiro-pr-companion/issues/3252) [#2739](https://github.com/echonuit/vigiechiro-pr-companion/issues/2739)
* **ci:** la republication de la doc suit de nouveau les captures ([#3282](https://github.com/echonuit/vigiechiro-pr-companion/issues/3282)) ([fc65fef](https://github.com/echonuit/vigiechiro-pr-companion/commit/fc65fef8043d7390a343b0b91234078076cb1f32)), closes [#1439](https://github.com/echonuit/vigiechiro-pr-companion/issues/1439) [#1439](https://github.com/echonuit/vigiechiro-pr-companion/issues/1439) [#3279](https://github.com/echonuit/vigiechiro-pr-companion/issues/3279)
* **ci:** réparer la publication des aperçus, cassée depuis [#3359](https://github.com/echonuit/vigiechiro-pr-companion/issues/3359) ([#3370](https://github.com/echonuit/vigiechiro-pr-companion/issues/3370)) ([3c4741b](https://github.com/echonuit/vigiechiro-pr-companion/commit/3c4741b6bb8fb0528a3b15351b0d716922680860))
* **cli:** le point se compare, parce que la sortie le distingue ([#3383](https://github.com/echonuit/vigiechiro-pr-companion/issues/3383)) ([a9df2e1](https://github.com/echonuit/vigiechiro-pr-companion/commit/a9df2e10f4e1a3f04a9b732793df881495d4e9a7)), closes [#3350](https://github.com/echonuit/vigiechiro-pr-companion/issues/3350)
* **cli:** ne plus viser un Provider avec une lambda, qu'ecj refuse ([#3367](https://github.com/echonuit/vigiechiro-pr-companion/issues/3367)) ([1b20fec](https://github.com/echonuit/vigiechiro-pr-companion/commit/1b20fec5c3995b4d38dc3bfe15804e62f5747698))
* **cloture:** ce que les 10 passes des suites de [#3092](https://github.com/echonuit/vigiechiro-pr-companion/issues/3092) ont trouvé ([#3339](https://github.com/echonuit/vigiechiro-pr-companion/issues/3339)) ([6be1353](https://github.com/echonuit/vigiechiro-pr-companion/commit/6be135379a1f43bf2fbd1a10e2810853e41b178b)), closes [#3095](https://github.com/echonuit/vigiechiro-pr-companion/issues/3095) [#2225](https://github.com/echonuit/vigiechiro-pr-companion/issues/2225) [#2989](https://github.com/echonuit/vigiechiro-pr-companion/issues/2989) [#3170](https://github.com/echonuit/vigiechiro-pr-companion/issues/3170) [#3337](https://github.com/echonuit/vigiechiro-pr-companion/issues/3337) [#3103](https://github.com/echonuit/vigiechiro-pr-companion/issues/3103) [#3151](https://github.com/echonuit/vigiechiro-pr-companion/issues/3151) [#3336](https://github.com/echonuit/vigiechiro-pr-companion/issues/3336) [#3330](https://github.com/echonuit/vigiechiro-pr-companion/issues/3330) [#3082](https://github.com/echonuit/vigiechiro-pr-companion/issues/3082) [#3082](https://github.com/echonuit/vigiechiro-pr-companion/issues/3082) [#3082](https://github.com/echonuit/vigiechiro-pr-companion/issues/3082) [#3082](https://github.com/echonuit/vigiechiro-pr-companion/issues/3082)
* **cloture:** ce que les passes du lot 3 ont trouvé ([#3295](https://github.com/echonuit/vigiechiro-pr-companion/issues/3295)) ([a1159ce](https://github.com/echonuit/vigiechiro-pr-companion/commit/a1159ce9f84ea687aecf048305d5454cd9e23522)), closes [#3254](https://github.com/echonuit/vigiechiro-pr-companion/issues/3254) [#3263](https://github.com/echonuit/vigiechiro-pr-companion/issues/3263) [#2744](https://github.com/echonuit/vigiechiro-pr-companion/issues/2744) [#2723](https://github.com/echonuit/vigiechiro-pr-companion/issues/2723)
* **commun:** une fenêtre porte son habillage, ou elle ne montre pas le produit ([#3375](https://github.com/echonuit/vigiechiro-pr-companion/issues/3375)) ([e3cda7a](https://github.com/echonuit/vigiechiro-pr-companion/commit/e3cda7a97e868d98e39ac69dbb43373500c6065c)), closes [#3361](https://github.com/echonuit/vigiechiro-pr-companion/issues/3361) [#3374](https://github.com/echonuit/vigiechiro-pr-companion/issues/3374) [#3364](https://github.com/echonuit/vigiechiro-pr-companion/issues/3364) [#3374](https://github.com/echonuit/vigiechiro-pr-companion/issues/3374)
* **commun:** une invite de recherche coupée net, et le garde qui ne la voyait pas ([#3286](https://github.com/echonuit/vigiechiro-pr-companion/issues/3286)) ([f644929](https://github.com/echonuit/vigiechiro-pr-companion/commit/f644929884a4684a179e6e1fb4f020bc8108f4ed))
* **deps:** nommer le vrai coupable de l'échéance Unsafe, et rendre analyze lisible ([#3369](https://github.com/echonuit/vigiechiro-pr-companion/issues/3369)) ([c30e9ad](https://github.com/echonuit/vigiechiro-pr-companion/commit/c30e9ad00701dddfc3bc05906f38261a4a3b88bf)), closes [#2740](https://github.com/echonuit/vigiechiro-pr-companion/issues/2740)
* **docs:** valider les ancres, et réparer les quatre qui étaient cassées ([#3360](https://github.com/echonuit/vigiechiro-pr-companion/issues/3360)) ([91c7b5e](https://github.com/echonuit/vigiechiro-pr-companion/commit/91c7b5e55a81c8940d6c363ceb501c3740e033df)), closes [#192](https://github.com/echonuit/vigiechiro-pr-companion/issues/192)
* **graphify:** un script deja dans le graphe ne se dedouble plus ([#3290](https://github.com/echonuit/vigiechiro-pr-companion/issues/3290)) ([3855c1b](https://github.com/echonuit/vigiechiro-pr-companion/commit/3855c1b1f035dea237c3f9ea17098f1a71bd605e)), closes [#3288](https://github.com/echonuit/vigiechiro-pr-companion/issues/3288)
* **ihm:** ce que l'application affiche tient dans la police embarquée ([#3399](https://github.com/echonuit/vigiechiro-pr-companion/issues/3399)) ([1ba019e](https://github.com/echonuit/vigiechiro-pr-companion/commit/1ba019e5d08a828e1a752e30e23bc644079a37bc)), closes [#3389](https://github.com/echonuit/vigiechiro-pr-companion/issues/3389)
* **saison:** montrer le nom du carré, par lequel la recherche trouve déjà ([#3297](https://github.com/echonuit/vigiechiro-pr-companion/issues/3297)) ([7673772](https://github.com/echonuit/vigiechiro-pr-companion/commit/7673772c2e0ebdd8a735f4e4e1d331420ee06992)), closes [#3219](https://github.com/echonuit/vigiechiro-pr-companion/issues/3219) [#3219](https://github.com/echonuit/vigiechiro-pr-companion/issues/3219) [#3289](https://github.com/echonuit/vigiechiro-pr-companion/issues/3289)


### Features

* **audio:** ce qui se cherche doit pouvoir se balayer ([#3393](https://github.com/echonuit/vigiechiro-pr-companion/issues/3393)) ([505fc00](https://github.com/echonuit/vigiechiro-pr-companion/commit/505fc00fd72bd92cb9296baefc543728d7dca8c1)), closes [#3300](https://github.com/echonuit/vigiechiro-pr-companion/issues/3300) [#3348](https://github.com/echonuit/vigiechiro-pr-companion/issues/3348) [#3375](https://github.com/echonuit/vigiechiro-pr-companion/issues/3375) [#3391](https://github.com/echonuit/vigiechiro-pr-companion/issues/3391) [#3389](https://github.com/echonuit/vigiechiro-pr-companion/issues/3389) [#3391](https://github.com/echonuit/vigiechiro-pr-companion/issues/3391) [#3389](https://github.com/echonuit/vigiechiro-pr-companion/issues/3389)
* **audio:** la certitude se filtre aussi à l'écran ([#3349](https://github.com/echonuit/vigiechiro-pr-companion/issues/3349)) ([241d4b4](https://github.com/echonuit/vigiechiro-pr-companion/commit/241d4b42885cca4aa20498ab0a3e784db46e6313)), closes [#3092](https://github.com/echonuit/vigiechiro-pr-companion/issues/3092) [#3105](https://github.com/echonuit/vigiechiro-pr-companion/issues/3105) [#3336](https://github.com/echonuit/vigiechiro-pr-companion/issues/3336)
* **captures:** montrer la valeur hors jeu, sans toucher à aucune graine ([#3299](https://github.com/echonuit/vigiechiro-pr-companion/issues/3299)) ([8dbcbd8](https://github.com/echonuit/vigiechiro-pr-companion/commit/8dbcbd8b1092f1b374d8b47984c82e27ff1ce0fb)), closes [#3092](https://github.com/echonuit/vigiechiro-pr-companion/issues/3092)
* **ci:** garder les renvois entre workflows contre un libellé mort ([#3355](https://github.com/echonuit/vigiechiro-pr-companion/issues/3355)) ([3cd311f](https://github.com/echonuit/vigiechiro-pr-companion/commit/3cd311f3e8f6ed8079e875976993ff8e0558bb35)), closes [#1439](https://github.com/echonuit/vigiechiro-pr-companion/issues/1439) [#3335](https://github.com/echonuit/vigiechiro-pr-companion/issues/3335)
* **ci:** la publication part à heure fixe, plus à chaque fusion ([#3283](https://github.com/echonuit/vigiechiro-pr-companion/issues/3283)) ([b6330da](https://github.com/echonuit/vigiechiro-pr-companion/commit/b6330da37e00817f27328d15682c35f37f57f058)), closes [#1363](https://github.com/echonuit/vigiechiro-pr-companion/issues/1363) [#2744](https://github.com/echonuit/vigiechiro-pr-companion/issues/2744) [#2191](https://github.com/echonuit/vigiechiro-pr-companion/issues/2191) [#2744](https://github.com/echonuit/vigiechiro-pr-companion/issues/2744)
* **ci:** une garde maintient le moindre privilège des workflows ([#3305](https://github.com/echonuit/vigiechiro-pr-companion/issues/3305)) ([4a2c749](https://github.com/echonuit/vigiechiro-pr-companion/commit/4a2c74959869216c07ffbb40c49cf99798c7d21a)), closes [#2739](https://github.com/echonuit/vigiechiro-pr-companion/issues/2739) [#2742](https://github.com/echonuit/vigiechiro-pr-companion/issues/2742) [#2739](https://github.com/echonuit/vigiechiro-pr-companion/issues/2739) [#3294](https://github.com/echonuit/vigiechiro-pr-companion/issues/3294) [#3294](https://github.com/echonuit/vigiechiro-pr-companion/issues/3294)
* **cli:** les deux inventaires d'espèces, posables sans ouvrir l'application ([#3323](https://github.com/echonuit/vigiechiro-pr-companion/issues/3323)) ([b7f08bb](https://github.com/echonuit/vigiechiro-pr-companion/commit/b7f08bb63748926e2e3ceced0d9579a5c96a3017)), closes [#3269](https://github.com/echonuit/vigiechiro-pr-companion/issues/3269) [#3059](https://github.com/echonuit/vigiechiro-pr-companion/issues/3059) [#3269](https://github.com/echonuit/vigiechiro-pr-companion/issues/3269)
* **cli:** les trois derniers filtres de lister-passages, en étendant la lecture ([#3320](https://github.com/echonuit/vigiechiro-pr-companion/issues/3320)) ([d3fa103](https://github.com/echonuit/vigiechiro-pr-companion/commit/d3fa103b979e1aa34fb40789330e9b530bb0fb6c))
* **cli:** quatre filtres pour lister-passages, sur le prédicat de l'écran ([#3316](https://github.com/echonuit/vigiechiro-pr-companion/issues/3316)) ([8931490](https://github.com/echonuit/vigiechiro-pr-companion/commit/89314908c27d96b36e7f610b0682ba7a6a7fe5a5))
* **ihm:** embarquer la monospace, alias que chaque système résout autrement ([#3413](https://github.com/echonuit/vigiechiro-pr-companion/issues/3413)) ([333ada9](https://github.com/echonuit/vigiechiro-pr-companion/commit/333ada9e68dc173d6523e7399c8234f057b61d50)), closes [#3412](https://github.com/echonuit/vigiechiro-pr-companion/issues/3412)
* **ihm:** embarquer la typographie, que le produit empruntait à la machine ([#3364](https://github.com/echonuit/vigiechiro-pr-companion/issues/3364)) ([a017d7d](https://github.com/echonuit/vigiechiro-pr-companion/commit/a017d7d838f2ac33aecb85f2aefeb6a9f73220f9)), closes [#3361](https://github.com/echonuit/vigiechiro-pr-companion/issues/3361)
* **ihm:** le nom du carré a sa colonne sur les deux écrans qui le cherchent ([#3310](https://github.com/echonuit/vigiechiro-pr-companion/issues/3310)) ([d094c8c](https://github.com/echonuit/vigiechiro-pr-companion/commit/d094c8c9b8590a4eeea363e98b00779da80f644e)), closes [#3289](https://github.com/echonuit/vigiechiro-pr-companion/issues/3289) [#919](https://github.com/echonuit/vigiechiro-pr-companion/issues/919)
* **saison:** offrir et montrer la commune, comme les quatre autres écrans ([#3317](https://github.com/echonuit/vigiechiro-pr-companion/issues/3317)) ([992c270](https://github.com/echonuit/vigiechiro-pr-companion/commit/992c270ad4976db2161c012d0f2cdfc57546d13e)), closes [#3151](https://github.com/echonuit/vigiechiro-pr-companion/issues/3151) [#3289](https://github.com/echonuit/vigiechiro-pr-companion/issues/3289) [#3289](https://github.com/echonuit/vigiechiro-pr-companion/issues/3289) [#3280](https://github.com/echonuit/vigiechiro-pr-companion/issues/3280)

# [2.183.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.182.0...v2.183.0) (2026-08-05)


### Features

* **cli:** audit-coherence gagne --contient, et dit ce que ses filtres masquent ([#3280](https://github.com/echonuit/vigiechiro-pr-companion/issues/3280)) ([c3455fb](https://github.com/echonuit/vigiechiro-pr-companion/commit/c3455fb4d3fb81bdce21d267cd51093a9732f9b9)), closes [#3258](https://github.com/echonuit/vigiechiro-pr-companion/issues/3258) [#3168](https://github.com/echonuit/vigiechiro-pr-companion/issues/3168) [#3092](https://github.com/echonuit/vigiechiro-pr-companion/issues/3092) [#3272](https://github.com/echonuit/vigiechiro-pr-companion/issues/3272) [#3272](https://github.com/echonuit/vigiechiro-pr-companion/issues/3272) [#3272](https://github.com/echonuit/vigiechiro-pr-companion/issues/3272) [#3258](https://github.com/echonuit/vigiechiro-pr-companion/issues/3258)

# [2.182.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.181.0...v2.182.0) (2026-08-05)


### Features

* **ci:** attester la provenance de chaque artefact publié ([#3278](https://github.com/echonuit/vigiechiro-pr-companion/issues/3278)) ([53312db](https://github.com/echonuit/vigiechiro-pr-companion/commit/53312dbd97b561e8859e476312954c9a596f8404)), closes [#2739](https://github.com/echonuit/vigiechiro-pr-companion/issues/2739) [#2742](https://github.com/echonuit/vigiechiro-pr-companion/issues/2742)

# [2.181.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.180.0...v2.181.0) (2026-08-05)


### Features

* **ci:** une garde refuse un jeton VigieChiro en clair ([#3274](https://github.com/echonuit/vigiechiro-pr-companion/issues/3274)) ([0bab426](https://github.com/echonuit/vigiechiro-pr-companion/commit/0bab426d400bd9fad2842101830743ac71851442)), closes [#2741](https://github.com/echonuit/vigiechiro-pr-companion/issues/2741) [#2741](https://github.com/echonuit/vigiechiro-pr-companion/issues/2741)

# [2.180.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.179.0...v2.180.0) (2026-08-05)


### Features

* **ci:** CodeQL cherche ce que ni PMD ni les tests ne cherchent ([#3263](https://github.com/echonuit/vigiechiro-pr-companion/issues/3263)) ([3b06062](https://github.com/echonuit/vigiechiro-pr-companion/commit/3b0606261b5216471cca2fb1e9234dee9f84c704)), closes [#169](https://github.com/echonuit/vigiechiro-pr-companion/issues/169) [#2741](https://github.com/echonuit/vigiechiro-pr-companion/issues/2741) [#2740](https://github.com/echonuit/vigiechiro-pr-companion/issues/2740) [#2741](https://github.com/echonuit/vigiechiro-pr-companion/issues/2741)

# [2.179.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.178.0...v2.179.0) (2026-08-04)


### Features

* **ci:** inventorier ce que le produit livre, et le surveiller ([#3262](https://github.com/echonuit/vigiechiro-pr-companion/issues/3262)) ([e61e78b](https://github.com/echonuit/vigiechiro-pr-companion/commit/e61e78bc5cfa8890b7d9411602181087893289c8)), closes [#2740](https://github.com/echonuit/vigiechiro-pr-companion/issues/2740)

# [2.178.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.177.0...v2.178.0) (2026-08-04)


### Features

* **sauvegarde:** la restauration montre ce qu'elle propose, au lieu d'un sélecteur aveugle ([#3248](https://github.com/echonuit/vigiechiro-pr-companion/issues/3248)) ([885863e](https://github.com/echonuit/vigiechiro-pr-companion/commit/885863e2ef37d098a41f37f97010781e95e7d9d6)), closes [#3197](https://github.com/echonuit/vigiechiro-pr-companion/issues/3197)

# [2.177.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.176.0...v2.177.0) (2026-08-04)


### Features

* **audit:** signaler quand les deux lectures d'un departement divergent ([#3246](https://github.com/echonuit/vigiechiro-pr-companion/issues/3246)) ([93e0555](https://github.com/echonuit/vigiechiro-pr-companion/commit/93e0555b7a1a63409894b768fcda17d2601b0cce)), closes [#1347](https://github.com/echonuit/vigiechiro-pr-companion/issues/1347) [#2848](https://github.com/echonuit/vigiechiro-pr-companion/issues/2848)

# [2.176.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.175.0...v2.176.0) (2026-08-04)


### Features

* **sauvegarde:** la CLI dit ce que le dossier de sauvegardes contient et ce qu'il pèse ([#3245](https://github.com/echonuit/vigiechiro-pr-companion/issues/3245)) ([c7e25c9](https://github.com/echonuit/vigiechiro-pr-companion/commit/c7e25c9aec0a13f91279598f45a8a1c620350080)), closes [#3197](https://github.com/echonuit/vigiechiro-pr-companion/issues/3197)

# [2.175.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.174.1...v2.175.0) (2026-08-04)


### Features

* **cli:** importer accepte une archive .zip, comme l'écran d'import ([#3244](https://github.com/echonuit/vigiechiro-pr-companion/issues/3244)) ([3d6660e](https://github.com/echonuit/vigiechiro-pr-companion/commit/3d6660e74a53ca13d6b48937cbb28202c47a2714)), closes [#3195](https://github.com/echonuit/vigiechiro-pr-companion/issues/3195) [#2732](https://github.com/echonuit/vigiechiro-pr-companion/issues/2732)

## [2.174.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.174.0...v2.174.1) (2026-08-04)


### Bug Fixes

* **securite:** une entrée externe se lit sous plafond, et le plafond vient d'une mesure ([#3238](https://github.com/echonuit/vigiechiro-pr-companion/issues/3238)) ([e6559a8](https://github.com/echonuit/vigiechiro-pr-companion/commit/e6559a8e954a4e18a3f4b8d474027685ef9bbd21)), closes [#2732](https://github.com/echonuit/vigiechiro-pr-companion/issues/2732) [#2732](https://github.com/echonuit/vigiechiro-pr-companion/issues/2732) [#3222](https://github.com/echonuit/vigiechiro-pr-companion/issues/3222) [#2354](https://github.com/echonuit/vigiechiro-pr-companion/issues/2354) [#2732](https://github.com/echonuit/vigiechiro-pr-companion/issues/2732)

# [2.174.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.173.1...v2.174.0) (2026-08-04)


### Features

* **analyse:** montrer la commune dans la table des observations ([#3233](https://github.com/echonuit/vigiechiro-pr-companion/issues/3233)) ([260f891](https://github.com/echonuit/vigiechiro-pr-companion/commit/260f891f0de43a7e1a59bff0b942d5f491d618f3)), closes [#3160](https://github.com/echonuit/vigiechiro-pr-companion/issues/3160) [#3165](https://github.com/echonuit/vigiechiro-pr-companion/issues/3165)

## [2.173.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.173.0...v2.173.1) (2026-08-04)


### Bug Fixes

* **importation:** la copie d'un enregistrement s'interrompt en cours de fichier ([#3232](https://github.com/echonuit/vigiechiro-pr-companion/issues/3232)) ([949b316](https://github.com/echonuit/vigiechiro-pr-companion/commit/949b316141b440ef049b6b2f681b0675f3d91311)), closes [#2733](https://github.com/echonuit/vigiechiro-pr-companion/issues/2733) [#3221](https://github.com/echonuit/vigiechiro-pr-companion/issues/3221) [#3221](https://github.com/echonuit/vigiechiro-pr-companion/issues/3221)

# [2.173.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.172.0...v2.173.0) (2026-08-04)


### Features

* **audio:** montrer la commune dans la table des observations ([#3229](https://github.com/echonuit/vigiechiro-pr-companion/issues/3229)) ([cad6b69](https://github.com/echonuit/vigiechiro-pr-companion/commit/cad6b694c47926aee9b795496b42bee675ea5221)), closes [#2790](https://github.com/echonuit/vigiechiro-pr-companion/issues/2790) [#1194](https://github.com/echonuit/vigiechiro-pr-companion/issues/1194) [#3164](https://github.com/echonuit/vigiechiro-pr-companion/issues/3164)

# [2.172.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.171.0...v2.172.0) (2026-08-04)


### Features

* **sauvegarde:** dire ce que l'archive emporte, au moment de l'écrire ([#3227](https://github.com/echonuit/vigiechiro-pr-companion/issues/3227)) ([1059a07](https://github.com/echonuit/vigiechiro-pr-companion/commit/1059a0780c6e66b15b408171fe6734cbac9333ad)), closes [#3212](https://github.com/echonuit/vigiechiro-pr-companion/issues/3212) [#3212](https://github.com/echonuit/vigiechiro-pr-companion/issues/3212)

# [2.171.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.170.0...v2.171.0) (2026-08-04)


### Features

* **multisite:** montrer la commune dans la table des passages ([#3226](https://github.com/echonuit/vigiechiro-pr-companion/issues/3226)) ([f6e7c98](https://github.com/echonuit/vigiechiro-pr-companion/commit/f6e7c98787e9b481115ebfe6135aab0e7fdffa12)), closes [#2790](https://github.com/echonuit/vigiechiro-pr-companion/issues/2790) [#3163](https://github.com/echonuit/vigiechiro-pr-companion/issues/3163)

# [2.170.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.169.1...v2.170.0) (2026-08-04)


### Features

* **multisite:** le CSV nomme le carré carre, et porte le nom du site ([#3223](https://github.com/echonuit/vigiechiro-pr-companion/issues/3223)) ([331d4a3](https://github.com/echonuit/vigiechiro-pr-companion/commit/331d4a3d212fd6f2a5f5d13aace8bc924dadacf0)), closes [#3192](https://github.com/echonuit/vigiechiro-pr-companion/issues/3192)
* **saison:** chercher un carré par le nom qu'on lui a donné ([#3219](https://github.com/echonuit/vigiechiro-pr-companion/issues/3219)) ([ab2742e](https://github.com/echonuit/vigiechiro-pr-companion/commit/ab2742ec72ab3f39f2ddc0c33a5470c89a5f7746)), closes [#3215](https://github.com/echonuit/vigiechiro-pr-companion/issues/3215)

## [2.169.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.169.0...v2.169.1) (2026-08-04)


### Bug Fixes

* **api:** vérifier une URL pré-signée avant de la suivre (https + hôte attendu) ([#3208](https://github.com/echonuit/vigiechiro-pr-companion/issues/3208)) ([f6afc9d](https://github.com/echonuit/vigiechiro-pr-companion/commit/f6afc9d52a5f48321da86dbc0c97cc6b61fedbe8)), closes [#2734](https://github.com/echonuit/vigiechiro-pr-companion/issues/2734) [#2734](https://github.com/echonuit/vigiechiro-pr-companion/issues/2734)

# [2.169.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.168.0...v2.169.0) (2026-08-04)


### Features

* **analyse:** offrir le point d'écoute au critère Lieu ([#3206](https://github.com/echonuit/vigiechiro-pr-companion/issues/3206)) ([f759802](https://github.com/echonuit/vigiechiro-pr-companion/commit/f75980218d8aaafa66bcbc76e6c6e3e8d1f9b593)), closes [#2992](https://github.com/echonuit/vigiechiro-pr-companion/issues/2992) [#3161](https://github.com/echonuit/vigiechiro-pr-companion/issues/3161)

# [2.168.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.167.0...v2.168.0) (2026-08-04)


### Features

* **saison:** chercher un lieu et isoler ce qui reste a faire ([#3205](https://github.com/echonuit/vigiechiro-pr-companion/issues/3205)) ([6ada547](https://github.com/echonuit/vigiechiro-pr-companion/commit/6ada547525e3ae0cdc62225261b192058f944745))

# [2.167.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.166.2...v2.167.0) (2026-08-04)


### Features

* **analyse:** porter le code du point sur la projection du filtre ([#3203](https://github.com/echonuit/vigiechiro-pr-companion/issues/3203)) ([234a4e5](https://github.com/echonuit/vigiechiro-pr-companion/commit/234a4e566443091451d47719f418ae890176666f)), closes [#3160](https://github.com/echonuit/vigiechiro-pr-companion/issues/3160)
* **audit:** installer la barre de filtres sur l'Audit de coherence ([#3201](https://github.com/echonuit/vigiechiro-pr-companion/issues/3201)) ([3d4a4c3](https://github.com/echonuit/vigiechiro-pr-companion/commit/3d4a4c39a967675da1d74f4966071db513dc5c82)), closes [#3056](https://github.com/echonuit/vigiechiro-pr-companion/issues/3056) [#3095](https://github.com/echonuit/vigiechiro-pr-companion/issues/3095) [#3096](https://github.com/echonuit/vigiechiro-pr-companion/issues/3096) [#3169](https://github.com/echonuit/vigiechiro-pr-companion/issues/3169)

## [2.166.2](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.166.1...v2.166.2) (2026-08-04)


### Bug Fixes

* **connexion:** le token ne passe plus par un fichier permissif, et s'écrit d'un seul coup ([#3202](https://github.com/echonuit/vigiechiro-pr-companion/issues/3202)) ([70300a3](https://github.com/echonuit/vigiechiro-pr-companion/commit/70300a323bf446b880f3bfd23335dcb67cfb11e9)), closes [#2735](https://github.com/echonuit/vigiechiro-pr-companion/issues/2735) [#2736](https://github.com/echonuit/vigiechiro-pr-companion/issues/2736) [#1140](https://github.com/echonuit/vigiechiro-pr-companion/issues/1140) [#2735](https://github.com/echonuit/vigiechiro-pr-companion/issues/2735)

## [2.166.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.166.0...v2.166.1) (2026-08-04)


### Bug Fixes

* **importation:** borner la décompression en ressources, pas seulement en chemins ([#3196](https://github.com/echonuit/vigiechiro-pr-companion/issues/3196)) ([dbd23f3](https://github.com/echonuit/vigiechiro-pr-companion/commit/dbd23f3fd34c56c93508688c7683445b9f5a528a)), closes [#2732](https://github.com/echonuit/vigiechiro-pr-companion/issues/2732) [#2041](https://github.com/echonuit/vigiechiro-pr-companion/issues/2041) [#2733](https://github.com/echonuit/vigiechiro-pr-companion/issues/2733) [#2733](https://github.com/echonuit/vigiechiro-pr-companion/issues/2733) [#2076](https://github.com/echonuit/vigiechiro-pr-companion/issues/2076) [#3195](https://github.com/echonuit/vigiechiro-pr-companion/issues/3195) [#2732](https://github.com/echonuit/vigiechiro-pr-companion/issues/2732)

# [2.166.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.165.2...v2.166.0) (2026-08-04)


### Features

* **commun:** memoire de session sur les quatre ecrans, avec Tout effacer ([#3188](https://github.com/echonuit/vigiechiro-pr-companion/issues/3188)) ([bb695a9](https://github.com/echonuit/vigiechiro-pr-companion/commit/bb695a9bc053cba83dcd780e0cf5195a747c0683)), closes [#3093](https://github.com/echonuit/vigiechiro-pr-companion/issues/3093) [#3098](https://github.com/echonuit/vigiechiro-pr-companion/issues/3098)

## [2.165.2](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.165.1...v2.165.2) (2026-08-04)


### Bug Fixes

* **annulation:** une copie longue s'interrompt en cours de route, dans les deux sens ([#3187](https://github.com/echonuit/vigiechiro-pr-companion/issues/3187)) ([b537bfe](https://github.com/echonuit/vigiechiro-pr-companion/commit/b537bfe509ad6b07637f4c688aef6aeacc9ffffe)), closes [#104](https://github.com/echonuit/vigiechiro-pr-companion/issues/104) [#2733](https://github.com/echonuit/vigiechiro-pr-companion/issues/2733) [#2712](https://github.com/echonuit/vigiechiro-pr-companion/issues/2712) [ZipEntry#getSize](https://github.com/ZipEntry/issues/getSize) [#2733](https://github.com/echonuit/vigiechiro-pr-companion/issues/2733)

## [2.165.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.165.0...v2.165.1) (2026-08-04)


### Bug Fixes

* **cli:** le refus de --lieu nomme les carrés comme l'écran les montre ([#3185](https://github.com/echonuit/vigiechiro-pr-companion/issues/3185)) ([ba077a8](https://github.com/echonuit/vigiechiro-pr-companion/commit/ba077a8d5c28d0c11caca7fcce4bb3853765ba49)), closes [#3159](https://github.com/echonuit/vigiechiro-pr-companion/issues/3159) [#3159](https://github.com/echonuit/vigiechiro-pr-companion/issues/3159)

# [2.165.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.164.0...v2.165.0) (2026-08-04)


### Features

* **commun:** un carré, une entrée, ses deux étiquettes ([#3183](https://github.com/echonuit/vigiechiro-pr-companion/issues/3183)) ([7ae13ef](https://github.com/echonuit/vigiechiro-pr-companion/commit/7ae13ef3df41fe31b0d56430cb9ea2bdc5082531)), closes [#3158](https://github.com/echonuit/vigiechiro-pr-companion/issues/3158) [#2992](https://github.com/echonuit/vigiechiro-pr-companion/issues/2992) [#3157](https://github.com/echonuit/vigiechiro-pr-companion/issues/3157) [#3145](https://github.com/echonuit/vigiechiro-pr-companion/issues/3145)

# [2.164.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.163.0...v2.164.0) (2026-08-04)


### Features

* **analyse:** porter le nom convivial du site sur les projections de passage et de contact ([#3178](https://github.com/echonuit/vigiechiro-pr-companion/issues/3178)) ([28764a2](https://github.com/echonuit/vigiechiro-pr-companion/commit/28764a218faa4f934c25f9e8110b5ee02ff4bbca)), closes [#3175](https://github.com/echonuit/vigiechiro-pr-companion/issues/3175) [#3157](https://github.com/echonuit/vigiechiro-pr-companion/issues/3157) [#3175](https://github.com/echonuit/vigiechiro-pr-companion/issues/3175)

# [2.163.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.162.2...v2.163.0) (2026-08-04)


### Features

* **persistance:** un refus et une panne cessent de se dire avec le même mot ([#3177](https://github.com/echonuit/vigiechiro-pr-companion/issues/3177)) ([8cfc662](https://github.com/echonuit/vigiechiro-pr-companion/commit/8cfc66229d8ac5a26b8c3055bbc3c71955ddd170)), closes [#2294](https://github.com/echonuit/vigiechiro-pr-companion/issues/2294) [#3146](https://github.com/echonuit/vigiechiro-pr-companion/issues/3146) [#3146](https://github.com/echonuit/vigiechiro-pr-companion/issues/3146)

## [2.162.2](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.162.1...v2.162.2) (2026-08-04)


### Bug Fixes

* **commun:** une valeur de lieu mémorisée avant qualification retrouve son entrée ([#3172](https://github.com/echonuit/vigiechiro-pr-companion/issues/3172)) ([11e8e99](https://github.com/echonuit/vigiechiro-pr-companion/commit/11e8e99f75d21f044e9675999768da906bd4e901)), closes [#3093](https://github.com/echonuit/vigiechiro-pr-companion/issues/3093) [#3158](https://github.com/echonuit/vigiechiro-pr-companion/issues/3158) [#2992](https://github.com/echonuit/vigiechiro-pr-companion/issues/2992) [#3093](https://github.com/echonuit/vigiechiro-pr-companion/issues/3093) [#3158](https://github.com/echonuit/vigiechiro-pr-companion/issues/3158)

## [2.162.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.162.0...v2.162.1) (2026-08-03)


### Bug Fixes

* **commun:** nommer les criteres en francais dans les comptes rendus ([#3167](https://github.com/echonuit/vigiechiro-pr-companion/issues/3167)) ([6871734](https://github.com/echonuit/vigiechiro-pr-companion/commit/6871734b4636dd2bc16301ae482f468d5fcff82d)), closes [#3092](https://github.com/echonuit/vigiechiro-pr-companion/issues/3092)

# [2.162.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.161.0...v2.162.0) (2026-08-03)


### Features

* **compte-rendu:** montrer les comptes rendus de restauration, et corriger ce qu'ils montrent ([#3166](https://github.com/echonuit/vigiechiro-pr-companion/issues/3166)) ([1351998](https://github.com/echonuit/vigiechiro-pr-companion/commit/1351998b86459409f0985fb6612a0fac6bb36d52)), closes [#3148](https://github.com/echonuit/vigiechiro-pr-companion/issues/3148) [#2727](https://github.com/echonuit/vigiechiro-pr-companion/issues/2727) [#3148](https://github.com/echonuit/vigiechiro-pr-companion/issues/3148)

# [2.161.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.160.0...v2.161.0) (2026-08-03)


### Features

* **workspace:** un seul processus écrit dans un dossier de travail ([#3141](https://github.com/echonuit/vigiechiro-pr-companion/issues/3141)) ([2adfdb1](https://github.com/echonuit/vigiechiro-pr-companion/commit/2adfdb19880ac964cbf011118a44c4a60d5d4e10)), closes [#2728](https://github.com/echonuit/vigiechiro-pr-companion/issues/2728) [#2729](https://github.com/echonuit/vigiechiro-pr-companion/issues/2729) [#2727](https://github.com/echonuit/vigiechiro-pr-companion/issues/2727) [#2730](https://github.com/echonuit/vigiechiro-pr-companion/issues/2730) [#2731](https://github.com/echonuit/vigiechiro-pr-companion/issues/2731) [#2731](https://github.com/echonuit/vigiechiro-pr-companion/issues/2731)

# [2.160.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.159.0...v2.160.0) (2026-08-03)


### Features

* **commun:** cascader les domaines des trois ecrans restants ([#3139](https://github.com/echonuit/vigiechiro-pr-companion/issues/3139)) ([320871d](https://github.com/echonuit/vigiechiro-pr-companion/commit/320871d2dc7394f53bb4d4b707e02d3c9cc88c90)), closes [#3136](https://github.com/echonuit/vigiechiro-pr-companion/issues/3136) [#3095](https://github.com/echonuit/vigiechiro-pr-companion/issues/3095)

# [2.159.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.158.0...v2.159.0) (2026-08-03)


### Features

* **commun:** cascader les listes de valeurs sur les autres criteres ([#3136](https://github.com/echonuit/vigiechiro-pr-companion/issues/3136)) ([45a8219](https://github.com/echonuit/vigiechiro-pr-companion/commit/45a821951aa2d879b9b617404326a6a2d269d954)), closes [#3095](https://github.com/echonuit/vigiechiro-pr-companion/issues/3095)

# [2.158.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.157.0...v2.158.0) (2026-08-03)


### Bug Fixes

* **commun:** une valeur choisie se retrouve par son identite, pas par son rang ([#3131](https://github.com/echonuit/vigiechiro-pr-companion/issues/3131)) ([8eb5944](https://github.com/echonuit/vigiechiro-pr-companion/commit/8eb594417449e085162a5ce5d271acf1b5a818f6)), closes [#3095](https://github.com/echonuit/vigiechiro-pr-companion/issues/3095) [#3071](https://github.com/echonuit/vigiechiro-pr-companion/issues/3071) [#3095](https://github.com/echonuit/vigiechiro-pr-companion/issues/3095) [#3128](https://github.com/echonuit/vigiechiro-pr-companion/issues/3128)
* **multisite:** une annee illisible cesse de se faire passer pour un filtre actif ([#3125](https://github.com/echonuit/vigiechiro-pr-companion/issues/3125)) ([013f76e](https://github.com/echonuit/vigiechiro-pr-companion/commit/013f76e39a76e695829c90f152dd837534cccf8e)), closes [#2119](https://github.com/echonuit/vigiechiro-pr-companion/issues/2119) [#3094](https://github.com/echonuit/vigiechiro-pr-companion/issues/3094)


### Features

* **restauration:** refuser une sauvegarde trop récente, et revenir en arrière ([#3135](https://github.com/echonuit/vigiechiro-pr-companion/issues/3135)) ([4170ad2](https://github.com/echonuit/vigiechiro-pr-companion/commit/4170ad2c8fa2d2a077a3e538f6c8c431cd3f7241)), closes [#2729](https://github.com/echonuit/vigiechiro-pr-companion/issues/2729) [#2730](https://github.com/echonuit/vigiechiro-pr-companion/issues/2730) [#2727](https://github.com/echonuit/vigiechiro-pr-companion/issues/2727) [#2726](https://github.com/echonuit/vigiechiro-pr-companion/issues/2726) [#2727](https://github.com/echonuit/vigiechiro-pr-companion/issues/2727) [#2730](https://github.com/echonuit/vigiechiro-pr-companion/issues/2730) [#2730](https://github.com/echonuit/vigiechiro-pr-companion/issues/2730)
* **restauration:** remettre les dossiers de son là où ils étaient ([#3124](https://github.com/echonuit/vigiechiro-pr-companion/issues/3124)) ([038f240](https://github.com/echonuit/vigiechiro-pr-companion/commit/038f240f6866068aacacad1b22644855ea946b53)), closes [#2727](https://github.com/echonuit/vigiechiro-pr-companion/issues/2727) [#2726](https://github.com/echonuit/vigiechiro-pr-companion/issues/2726) [#2727](https://github.com/echonuit/vigiechiro-pr-companion/issues/2727) [#2726](https://github.com/echonuit/vigiechiro-pr-companion/issues/2726) [#2727](https://github.com/echonuit/vigiechiro-pr-companion/issues/2727)

# [2.157.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.156.0...v2.157.0) (2026-08-03)


### Features

* **sauvegarde:** garder d'où venaient les dossiers, et cesser de les confondre ([#3122](https://github.com/echonuit/vigiechiro-pr-companion/issues/3122)) ([bf0e460](https://github.com/echonuit/vigiechiro-pr-companion/commit/bf0e4608878f1ebe5516c97513448f81fb7eb47d)), closes [#2727](https://github.com/echonuit/vigiechiro-pr-companion/issues/2727) [#2726](https://github.com/echonuit/vigiechiro-pr-companion/issues/2726) [#2726](https://github.com/echonuit/vigiechiro-pr-companion/issues/2726)

# [2.156.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.155.0...v2.156.0) (2026-08-03)


### Bug Fixes

* **commun:** un filtre restauré ne se perd plus en silence ([#3119](https://github.com/echonuit/vigiechiro-pr-companion/issues/3119)) ([aaa963e](https://github.com/echonuit/vigiechiro-pr-companion/commit/aaa963e823731c79e39047f9c4b53b1b7d8219cb)), closes [#3056](https://github.com/echonuit/vigiechiro-pr-companion/issues/3056) [#3093](https://github.com/echonuit/vigiechiro-pr-companion/issues/3093) [#476](https://github.com/echonuit/vigiechiro-pr-companion/issues/476) [#484](https://github.com/echonuit/vigiechiro-pr-companion/issues/484)


### Features

* **migration:** mettre la base à l'abri avant de la faire évoluer ([#3109](https://github.com/echonuit/vigiechiro-pr-companion/issues/3109)) ([8b49cac](https://github.com/echonuit/vigiechiro-pr-companion/commit/8b49cacb17f279426fb117b4b5c04cb0111bf319)), closes [#2729](https://github.com/echonuit/vigiechiro-pr-companion/issues/2729) [#2728](https://github.com/echonuit/vigiechiro-pr-companion/issues/2728) [#2729](https://github.com/echonuit/vigiechiro-pr-companion/issues/2729)

# [2.155.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.154.1...v2.155.0) (2026-08-01)


### Features

* **cli:** les trois critères de revue qui manquaient en ligne de commande ([#3106](https://github.com/echonuit/vigiechiro-pr-companion/issues/3106)) ([8b0d4be](https://github.com/echonuit/vigiechiro-pr-companion/commit/8b0d4be0b764f5116930a9e8191007c8dcc07bd3)), closes [#2971](https://github.com/echonuit/vigiechiro-pr-companion/issues/2971) [#3082](https://github.com/echonuit/vigiechiro-pr-companion/issues/3082)

## [2.154.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.154.0...v2.154.1) (2026-08-01)


### Bug Fixes

* **captures:** attendre les tuiles sur une condition, non sur un délai ([#3102](https://github.com/echonuit/vigiechiro-pr-companion/issues/3102)) ([abf7765](https://github.com/echonuit/vigiechiro-pr-companion/commit/abf7765e87f631d64836bc095a6a1f57b918b53c)), closes [#3068](https://github.com/echonuit/vigiechiro-pr-companion/issues/3068) [#3085](https://github.com/echonuit/vigiechiro-pr-companion/issues/3085) [#3090](https://github.com/echonuit/vigiechiro-pr-companion/issues/3090) [#3050](https://github.com/echonuit/vigiechiro-pr-companion/issues/3050) [#3068](https://github.com/echonuit/vigiechiro-pr-companion/issues/3068)

# [2.154.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.153.3...v2.154.0) (2026-08-01)


### Features

* **migration:** un script publié ne se modifie plus, et la base s'en souvient ([#3104](https://github.com/echonuit/vigiechiro-pr-companion/issues/3104)) ([5247980](https://github.com/echonuit/vigiechiro-pr-companion/commit/524798004144a5d32ffcc0860983e55b1160237f)), closes [#2728](https://github.com/echonuit/vigiechiro-pr-companion/issues/2728) [#2729](https://github.com/echonuit/vigiechiro-pr-companion/issues/2729)

## [2.153.3](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.153.2...v2.153.3) (2026-08-01)


### Bug Fixes

* **migration:** une migration passe entière, ou pas du tout ([#3091](https://github.com/echonuit/vigiechiro-pr-companion/issues/3091)) ([adfb120](https://github.com/echonuit/vigiechiro-pr-companion/commit/adfb1204661b6ca906127100877f2dd9090f5940)), closes [#2728](https://github.com/echonuit/vigiechiro-pr-companion/issues/2728) [#2728](https://github.com/echonuit/vigiechiro-pr-companion/issues/2728) [#2728](https://github.com/echonuit/vigiechiro-pr-companion/issues/2728)

## [2.153.2](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.153.1...v2.153.2) (2026-07-31)


### Bug Fixes

* **synthese:** les quantiles ne se coupent plus, et un garde le vérifie ([#3085](https://github.com/echonuit/vigiechiro-pr-companion/issues/3085)) ([42137e2](https://github.com/echonuit/vigiechiro-pr-companion/commit/42137e27d28dd41fed8c89a71b4368ad82700d95))

## [2.153.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.153.0...v2.153.1) (2026-07-31)


### Bug Fixes

* **cli:** --a-enjeu dit quand le référentiel est vide, au lieu de se taire ([#3079](https://github.com/echonuit/vigiechiro-pr-companion/issues/3079)) ([65ff715](https://github.com/echonuit/vigiechiro-pr-companion/commit/65ff7156c8d1ae6256a03b574a2bb3a40062c751)), closes [#3059](https://github.com/echonuit/vigiechiro-pr-companion/issues/3059) [#2971](https://github.com/echonuit/vigiechiro-pr-companion/issues/2971) [#3059](https://github.com/echonuit/vigiechiro-pr-companion/issues/3059) [#3048](https://github.com/echonuit/vigiechiro-pr-companion/issues/3048)

# [2.153.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.152.0...v2.153.0) (2026-07-31)


### Bug Fixes

* **filtres:** memoriser la session par valeurs, plus par indices ([#3076](https://github.com/echonuit/vigiechiro-pr-companion/issues/3076)) ([e70b06e](https://github.com/echonuit/vigiechiro-pr-companion/commit/e70b06e9012affcc0d66881bccfffcf51f55788d)), closes [#3071](https://github.com/echonuit/vigiechiro-pr-companion/issues/3071)


### Features

* **cli:** exporter-activite accepte les cinq filtres de l'écran ([#3075](https://github.com/echonuit/vigiechiro-pr-companion/issues/3075)) ([f2e8cb4](https://github.com/echonuit/vigiechiro-pr-companion/commit/f2e8cb45ed557ea5404325844b8bb84677c7c6bc)), closes [#2971](https://github.com/echonuit/vigiechiro-pr-companion/issues/2971) [#3060](https://github.com/echonuit/vigiechiro-pr-companion/issues/3060) [#3059](https://github.com/echonuit/vigiechiro-pr-companion/issues/3059)

# [2.152.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.151.1...v2.152.0) (2026-07-31)


### Features

* **vues:** signaler une vue rejouee amputee de ses valeurs disparues ([#3066](https://github.com/echonuit/vigiechiro-pr-companion/issues/3066)) ([db0bad7](https://github.com/echonuit/vigiechiro-pr-companion/commit/db0bad7bb2d0ad4162ec1cf606ba0d2c8966b627)), closes [#3062](https://github.com/echonuit/vigiechiro-pr-companion/issues/3062) [#3056](https://github.com/echonuit/vigiechiro-pr-companion/issues/3056)

## [2.151.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.151.0...v2.151.1) (2026-07-31)


### Bug Fixes

* **synthese:** les clés du référentiel se lisent avant d'être montrées ([#3063](https://github.com/echonuit/vigiechiro-pr-companion/issues/3063)) ([ed86f68](https://github.com/echonuit/vigiechiro-pr-companion/commit/ed86f6881c1fb387a3b27e4c490ef1e1597c70ad))
* **synthese:** ne plus décrire une comparaison qui n'a pas eu lieu ([#3065](https://github.com/echonuit/vigiechiro-pr-companion/issues/3065)) ([11a101d](https://github.com/echonuit/vigiechiro-pr-companion/commit/11a101d95a40b6f299a9cb2cfaeefb3972733881))

# [2.151.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.150.4...v2.151.0) (2026-07-31)


### Features

* **filtres:** relever les valeurs memorisees sans correspondance ([#3062](https://github.com/echonuit/vigiechiro-pr-companion/issues/3062)) ([c4fd61f](https://github.com/echonuit/vigiechiro-pr-companion/commit/c4fd61f879943a811590c27da2a105b90aab5fda)), closes [#2995](https://github.com/echonuit/vigiechiro-pr-companion/issues/2995) [#3056](https://github.com/echonuit/vigiechiro-pr-companion/issues/3056)

## [2.150.4](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.150.3...v2.150.4) (2026-07-31)


### Bug Fixes

* **synthese:** le quatrième message de la colonne Activité est enfin montré ([#3057](https://github.com/echonuit/vigiechiro-pr-companion/issues/3057)) ([8c59dac](https://github.com/echonuit/vigiechiro-pr-companion/commit/8c59dac57d16abd360367298ea8f8153bb55e656))

## [2.150.3](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.150.2...v2.150.3) (2026-07-31)


### Bug Fixes

* **api:** refuser une lecture paginee arretee par son garde-fou ([#3055](https://github.com/echonuit/vigiechiro-pr-companion/issues/3055)) ([9d25d92](https://github.com/echonuit/vigiechiro-pr-companion/commit/9d25d92487ffc75dec946dde76126389fcc230ee)), closes [#1277](https://github.com/echonuit/vigiechiro-pr-companion/issues/1277) [#3046](https://github.com/echonuit/vigiechiro-pr-companion/issues/3046)

## [2.150.2](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.150.1...v2.150.2) (2026-07-31)


### Bug Fixes

* **synthese:** la nuit de démonstration produit enfin le nombre fondateur ([#3047](https://github.com/echonuit/vigiechiro-pr-companion/issues/3047)) ([642db60](https://github.com/echonuit/vigiechiro-pr-companion/commit/642db60fec1f91bbb5be4a3dae1d70bbbdebf252))

## [2.150.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.150.0...v2.150.1) (2026-07-31)


### Bug Fixes

* **captures:** une recherche par libellé qui échoue au lieu de mentir ([#3039](https://github.com/echonuit/vigiechiro-pr-companion/issues/3039)) ([ce0387c](https://github.com/echonuit/vigiechiro-pr-companion/commit/ce0387cf58522c9389f8184f656ba48c1e7447b2)), closes [#2967](https://github.com/echonuit/vigiechiro-pr-companion/issues/2967) [#2049](https://github.com/echonuit/vigiechiro-pr-companion/issues/2049) [#3034](https://github.com/echonuit/vigiechiro-pr-companion/issues/3034) [#3018](https://github.com/echonuit/vigiechiro-pr-companion/issues/3018)

# [2.150.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.149.0...v2.150.0) (2026-07-31)


### Features

* **cli:** le groupe api ouvre l'exploration, sans rendre les pièges (lot 4) ([#3029](https://github.com/echonuit/vigiechiro-pr-companion/issues/3029)) ([ed66a82](https://github.com/echonuit/vigiechiro-pr-companion/commit/ed66a8263babd281893cdb431309328d7fd55c46))

# [2.149.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.148.0...v2.149.0) (2026-07-31)


### Features

* **activite:** une puce « Lieu » qui filtre enfin par commune ([#3025](https://github.com/echonuit/vigiechiro-pr-companion/issues/3025)) ([f0b4a62](https://github.com/echonuit/vigiechiro-pr-companion/commit/f0b4a62b504af42d59db7c1146f1bf7a07ba6f90)), closes [#2992](https://github.com/echonuit/vigiechiro-pr-companion/issues/2992) [#2967](https://github.com/echonuit/vigiechiro-pr-companion/issues/2967)

# [2.148.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.147.0...v2.148.0) (2026-07-31)


### Features

* **cli:** lister-participations-vigiechiro donne l'identifiant qu'aucune commande ne fournissait (lot 3) ([#3022](https://github.com/echonuit/vigiechiro-pr-companion/issues/3022)) ([d65d079](https://github.com/echonuit/vigiechiro-pr-companion/commit/d65d07919d0301aab6917282910f0830af73f7d8))

# [2.147.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.146.0...v2.147.0) (2026-07-31)


### Features

* **cli:** lister-sites-vigiechiro répond, au lieu de rendre du JSON à recompter (lot 2) ([#3019](https://github.com/echonuit/vigiechiro-pr-companion/issues/3019)) ([e2dce7a](https://github.com/echonuit/vigiechiro-pr-companion/commit/e2dce7ac530230eae4355bb13b16f75d797aec25))

# [2.146.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.145.0...v2.146.0) (2026-07-31)


### Features

* **cli:** --proba-min, et le scénario fondateur traversé de bout en bout ([#3014](https://github.com/echonuit/vigiechiro-pr-companion/issues/3014)) ([da9ce98](https://github.com/echonuit/vigiechiro-pr-companion/commit/da9ce988d95e00399d2d06165195eae91ea4480c)), closes [#2971](https://github.com/echonuit/vigiechiro-pr-companion/issues/2971) [#2971](https://github.com/echonuit/vigiechiro-pr-companion/issues/2971) [#2971](https://github.com/echonuit/vigiechiro-pr-companion/issues/2971)

# [2.145.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.144.0...v2.145.0) (2026-07-31)


### Features

* **api:** le socle lit le catalogue, et dit ce qu'il n'a pas lu (lot 1) ([#3013](https://github.com/echonuit/vigiechiro-pr-companion/issues/3013)) ([748df89](https://github.com/echonuit/vigiechiro-pr-companion/commit/748df89a75d68b22889334a8486c67db9f4030c1)), closes [#1277](https://github.com/echonuit/vigiechiro-pr-companion/issues/1277)

# [2.144.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.143.0...v2.144.0) (2026-07-31)


### Features

* **cli:** --lieu restreint exporter-sons et lister-observations ([#3010](https://github.com/echonuit/vigiechiro-pr-companion/issues/3010)) ([ec63a44](https://github.com/echonuit/vigiechiro-pr-companion/commit/ec63a443f6118ba2ab46da9928662659fde5e1b6)), closes [#2992](https://github.com/echonuit/vigiechiro-pr-companion/issues/2992) [#2971](https://github.com/echonuit/vigiechiro-pr-companion/issues/2971)

# [2.143.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.142.1...v2.143.0) (2026-07-31)


### Features

* **api:** la carte des lectures devient du code, et se fait contredire (lot 0) ([#3009](https://github.com/echonuit/vigiechiro-pr-companion/issues/3009)) ([167eb92](https://github.com/echonuit/vigiechiro-pr-companion/commit/167eb922ec491ac91c7868b09054f7fe41c5938e))

## [2.142.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.142.0...v2.142.1) (2026-07-31)


### Bug Fixes

* **filtres:** la puce « Lieu » groupe ses valeurs et qualifie le point par son carré ([#2995](https://github.com/echonuit/vigiechiro-pr-companion/issues/2995)) ([9fb70da](https://github.com/echonuit/vigiechiro-pr-companion/commit/9fb70da0e09bc6e53cee2312e93b24c69c61c0d5)), closes [#2968](https://github.com/echonuit/vigiechiro-pr-companion/issues/2968) [#2794](https://github.com/echonuit/vigiechiro-pr-companion/issues/2794) [#2992](https://github.com/echonuit/vigiechiro-pr-companion/issues/2992)

# [2.142.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.141.0...v2.142.0) (2026-07-30)


### Features

* **multisite:** une puce « Lieu » filtre les passages par commune, carré ou point ([#2990](https://github.com/echonuit/vigiechiro-pr-companion/issues/2990)) ([c192acf](https://github.com/echonuit/vigiechiro-pr-companion/commit/c192acf8d4bedd251ee6698188700962432606ac)), closes [#2968](https://github.com/echonuit/vigiechiro-pr-companion/issues/2968)

# [2.141.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.140.0...v2.141.0) (2026-07-30)


### Features

* **analyse:** une puce « Lieu » restreint les espèces et observations ([#2986](https://github.com/echonuit/vigiechiro-pr-companion/issues/2986)) ([d411745](https://github.com/echonuit/vigiechiro-pr-companion/commit/d41174542c54dcad13f545db8f3af96311f6a510)), closes [#2794](https://github.com/echonuit/vigiechiro-pr-companion/issues/2794) [#2966](https://github.com/echonuit/vigiechiro-pr-companion/issues/2966)

# [2.140.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.139.0...v2.140.0) (2026-07-30)


### Features

* **ci:** le titre de PR refuse une élision sans apostrophe ([#2969](https://github.com/echonuit/vigiechiro-pr-companion/issues/2969)) ([38025ce](https://github.com/echonuit/vigiechiro-pr-companion/commit/38025ceb066353225dd0c21bcb977eb86a43b2a2)), closes [#2947](https://github.com/echonuit/vigiechiro-pr-companion/issues/2947)

# [2.139.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.138.1...v2.139.0) (2026-07-30)


### Features

* **qualite:** les deux conventions d'écriture de CONTRIBUTING sont gardées ([#2963](https://github.com/echonuit/vigiechiro-pr-companion/issues/2963)) ([4273a8b](https://github.com/echonuit/vigiechiro-pr-companion/commit/4273a8b1ed1cad65a3e0e776abbd413bed3b4451)), closes [#2365](https://github.com/echonuit/vigiechiro-pr-companion/issues/2365) [#2946](https://github.com/echonuit/vigiechiro-pr-companion/issues/2946)

## [2.138.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.138.0...v2.138.1) (2026-07-30)


### Bug Fixes

* **captures:** les graines de M-Sites et M-Import retrouvent leurs accents ([#2957](https://github.com/echonuit/vigiechiro-pr-companion/issues/2957)) ([06c2cdb](https://github.com/echonuit/vigiechiro-pr-companion/commit/06c2cdb5a679df72cfcc43cfb87bc2eb39fa1c8a)), closes [#2945](https://github.com/echonuit/vigiechiro-pr-companion/issues/2945)

# [2.138.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.137.3...v2.138.0) (2026-07-30)


### Features

* **ci:** le titre de PR est garde contre le tiret cadratin ([#2955](https://github.com/echonuit/vigiechiro-pr-companion/issues/2955)) ([4b2bf92](https://github.com/echonuit/vigiechiro-pr-companion/commit/4b2bf92fe5aad4e39f32f4b0e494355cfb430e9e)), closes [#2365](https://github.com/echonuit/vigiechiro-pr-companion/issues/2365) [#2947](https://github.com/echonuit/vigiechiro-pr-companion/issues/2947)

## [2.137.3](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.137.2...v2.137.3) (2026-07-30)


### Bug Fixes

* **cliquet:** le semis de topologie cessait de voir les fichiers en cours de migration ([#2948](https://github.com/echonuit/vigiechiro-pr-companion/issues/2948)) ([40fa94f](https://github.com/echonuit/vigiechiro-pr-companion/commit/40fa94f951c5fcdda7a50ccf5dad599eb0f404aa)), closes [#2714](https://github.com/echonuit/vigiechiro-pr-companion/issues/2714)

## [2.137.2](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.137.1...v2.137.2) (2026-07-30)


### Bug Fixes

* **adr:** la documentation de la racine echappait a toute zone ([#2938](https://github.com/echonuit/vigiechiro-pr-companion/issues/2938)) ([f70dec2](https://github.com/echonuit/vigiechiro-pr-companion/commit/f70dec203b14ffabc3bbe58242a9e8059d015fe9)), closes [#2365](https://github.com/echonuit/vigiechiro-pr-companion/issues/2365)

## [2.137.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.137.0...v2.137.1) (2026-07-30)


### Bug Fixes

* **cliquet:** le journal se compte sur son contenu, pas sur son nom ([#2930](https://github.com/echonuit/vigiechiro-pr-companion/issues/2930)) ([86ec1f1](https://github.com/echonuit/vigiechiro-pr-companion/commit/86ec1f105d09cd6cd85711f40854b387a18520d9)), closes [#2904](https://github.com/echonuit/vigiechiro-pr-companion/issues/2904)

# [2.137.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.136.1...v2.137.0) (2026-07-30)


### Features

* **cli:** exporter-sons emporte les observations et leurs sons en ZIP (lot D) ([#2919](https://github.com/echonuit/vigiechiro-pr-companion/issues/2919)) ([ac0c9b3](https://github.com/echonuit/vigiechiro-pr-companion/commit/ac0c9b32ffa1e05b127471d761d937f905a84940)), closes [#2793](https://github.com/echonuit/vigiechiro-pr-companion/issues/2793) [#2792](https://github.com/echonuit/vigiechiro-pr-companion/issues/2792) [#2795](https://github.com/echonuit/vigiechiro-pr-companion/issues/2795) [#2866](https://github.com/echonuit/vigiechiro-pr-companion/issues/2866) [#2909](https://github.com/echonuit/vigiechiro-pr-companion/issues/2909)

## [2.136.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.136.0...v2.136.1) (2026-07-30)


### Bug Fixes

* **analyse:** la garde de locale était aveugle aux appels multi-lignes, dont un fautif ([#2915](https://github.com/echonuit/vigiechiro-pr-companion/issues/2915)) ([0532963](https://github.com/echonuit/vigiechiro-pr-companion/commit/0532963c1f37e0bcaa4e0ec8275c46d3fa92b680)), closes [#2896](https://github.com/echonuit/vigiechiro-pr-companion/issues/2896)

# [2.136.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.135.3...v2.136.0) (2026-07-30)


### Features

* **audio:** le critere « Lieu » et la recherche geographique de la vue audio (lot C) ([#2910](https://github.com/echonuit/vigiechiro-pr-companion/issues/2910)) ([6fac58a](https://github.com/echonuit/vigiechiro-pr-companion/commit/6fac58abea8433a38f76e0191147731d5691d7a3)), closes [#2794](https://github.com/echonuit/vigiechiro-pr-companion/issues/2794) [#2791](https://github.com/echonuit/vigiechiro-pr-companion/issues/2791) [#2794](https://github.com/echonuit/vigiechiro-pr-companion/issues/2794)

## [2.135.3](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.135.2...v2.135.3) (2026-07-30)


### Bug Fixes

* **qualite:** le résumé de mutation dit ce que son score doit à l'épuisement ([#2898](https://github.com/echonuit/vigiechiro-pr-companion/issues/2898)) ([434708d](https://github.com/echonuit/vigiechiro-pr-companion/commit/434708dcbd172bb21283d2fce7b111341ede3af0)), closes [#2858](https://github.com/echonuit/vigiechiro-pr-companion/issues/2858)

## [2.135.2](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.135.1...v2.135.2) (2026-07-30)


### Bug Fixes

* **outils:** mesurer le vrai bandeau, et corriger les hauteurs qu'il justifie ([#2902](https://github.com/echonuit/vigiechiro-pr-companion/issues/2902)) ([4143470](https://github.com/echonuit/vigiechiro-pr-companion/commit/4143470fecbd384522478283d73f67ab8a582223)), closes [#2897](https://github.com/echonuit/vigiechiro-pr-companion/issues/2897)

## [2.135.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.135.0...v2.135.1) (2026-07-30)


### Bug Fixes

* **analyse:** la probabilité d'un taxon ne suit plus la locale de la machine ([#2901](https://github.com/echonuit/vigiechiro-pr-companion/issues/2901)) ([3ff1584](https://github.com/echonuit/vigiechiro-pr-companion/commit/3ff1584991b5d0f330dd66d8a6a942dfd7d2fc16)), closes [#2757](https://github.com/echonuit/vigiechiro-pr-companion/issues/2757) [#2896](https://github.com/echonuit/vigiechiro-pr-companion/issues/2896)

# [2.135.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.134.0...v2.135.0) (2026-07-30)


### Features

* **audio:** le geste « Exporter les observations et les sons (ZIP) » (lot B) ([#2886](https://github.com/echonuit/vigiechiro-pr-companion/issues/2886)) ([41b50a0](https://github.com/echonuit/vigiechiro-pr-companion/commit/41b50a0bcd04fecc3c97d46309cb3acc9ec02f0b)), closes [#2793](https://github.com/echonuit/vigiechiro-pr-companion/issues/2793) [#2426](https://github.com/echonuit/vigiechiro-pr-companion/issues/2426) [#2793](https://github.com/echonuit/vigiechiro-pr-companion/issues/2793) [#149](https://github.com/echonuit/vigiechiro-pr-companion/issues/149) [#2793](https://github.com/echonuit/vigiechiro-pr-companion/issues/2793)

# [2.134.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.133.0...v2.134.0) (2026-07-30)


### Features

* **commun:** le port de compte rendu transporte des chiffres, et le lot en profite ([#2883](https://github.com/echonuit/vigiechiro-pr-companion/issues/2883)) ([30ff64f](https://github.com/echonuit/vigiechiro-pr-companion/commit/30ff64fb991c929b729d366a16178753fdbf2051))

# [2.133.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.132.0...v2.133.0) (2026-07-30)


### Features

* **fixture:** une entrée qui s'arrête au point d'écoute, et l'angle mort qu'elle révèle ([#2882](https://github.com/echonuit/vigiechiro-pr-companion/issues/2882)) ([8190a7e](https://github.com/echonuit/vigiechiro-pr-companion/commit/8190a7e609ff57aecafe5d843eb43e0682e4eaa8))

# [2.132.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.131.0...v2.132.0) (2026-07-30)


### Features

* **multisite:** le relevé groupé rend ses comptes en proportions, plus en phrase ([#2877](https://github.com/echonuit/vigiechiro-pr-companion/issues/2877)) ([db2ca1d](https://github.com/echonuit/vigiechiro-pr-companion/commit/db2ca1d11d7e9c1765f0b4c9cdc3317d308a1c0a)), closes [#2757](https://github.com/echonuit/vigiechiro-pr-companion/issues/2757)

# [2.131.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.130.0...v2.131.0) (2026-07-30)


### Features

* **validation:** le socle de l'export « observations + sons » (lot A) ([#2870](https://github.com/echonuit/vigiechiro-pr-companion/issues/2870)) ([96ad495](https://github.com/echonuit/vigiechiro-pr-companion/commit/96ad495dcb490376a6b4afd6dae4717c9dd4a761)), closes [#2792](https://github.com/echonuit/vigiechiro-pr-companion/issues/2792) [#2792](https://github.com/echonuit/vigiechiro-pr-companion/issues/2792) [#2790](https://github.com/echonuit/vigiechiro-pr-companion/issues/2790) [#1771](https://github.com/echonuit/vigiechiro-pr-companion/issues/1771)

# [2.130.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.129.2...v2.130.0) (2026-07-30)


### Features

* **captures:** trois aperçus d'un bandeau portant un message venu d'ailleurs ([#2869](https://github.com/echonuit/vigiechiro-pr-companion/issues/2869)) ([e6c081a](https://github.com/echonuit/vigiechiro-pr-companion/commit/e6c081ac1b03222989fcaba6f539ad3d4e482161)), closes [#2076](https://github.com/echonuit/vigiechiro-pr-companion/issues/2076) [#2841](https://github.com/echonuit/vigiechiro-pr-companion/issues/2841) [#2852](https://github.com/echonuit/vigiechiro-pr-companion/issues/2852)

## [2.129.2](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.129.1...v2.129.2) (2026-07-30)


### Bug Fixes

* **recherche:** la commune vaut aussi pour la recherche globale (ceremonie lot 0) ([#2856](https://github.com/echonuit/vigiechiro-pr-companion/issues/2856)) ([5a77f11](https://github.com/echonuit/vigiechiro-pr-companion/commit/5a77f11c47e5ceaf6c0ae42fc82e0d79cc46ca8a)), closes [#2827](https://github.com/echonuit/vigiechiro-pr-companion/issues/2827) [#2791](https://github.com/echonuit/vigiechiro-pr-companion/issues/2791)

## [2.129.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.129.0...v2.129.1) (2026-07-30)


### Bug Fixes

* **commun:** la frise n'accueille plus par défaut, et deux pastilles se lisent enfin ([#2854](https://github.com/echonuit/vigiechiro-pr-companion/issues/2854)) ([c46966e](https://github.com/echonuit/vigiechiro-pr-companion/commit/c46966e070df448c391d85cba239bc614969fd9a)), closes [#2833](https://github.com/echonuit/vigiechiro-pr-companion/issues/2833) [#2628](https://github.com/echonuit/vigiechiro-pr-companion/issues/2628)

# [2.129.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.128.1...v2.129.0) (2026-07-30)


### Features

* **qualite:** un cliquet pour la regle du tiret cadratin ([#2853](https://github.com/echonuit/vigiechiro-pr-companion/issues/2853)) ([de82a93](https://github.com/echonuit/vigiechiro-pr-companion/commit/de82a93b1d8274213356898c77ca47e1b7da5098)), closes [#2348](https://github.com/echonuit/vigiechiro-pr-companion/issues/2348) [#2843](https://github.com/echonuit/vigiechiro-pr-companion/issues/2843)

## [2.128.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.128.0...v2.128.1) (2026-07-29)


### Bug Fixes

* **retour:** la porte de [#2076](https://github.com/echonuit/vigiechiro-pr-companion/issues/2076) était contournée d'un saut, par douze appels ([#2844](https://github.com/echonuit/vigiechiro-pr-companion/issues/2844)) ([891b2ac](https://github.com/echonuit/vigiechiro-pr-companion/commit/891b2ac8fbdda679fd1b0802fcd20f469ea2b5b9)), closes [#2841](https://github.com/echonuit/vigiechiro-pr-companion/issues/2841)

# [2.128.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.127.0...v2.128.0) (2026-07-29)


### Features

* **multisite:** la commune se cherche dans Analyse et Carte & passages (lot 0, fin) ([#2840](https://github.com/echonuit/vigiechiro-pr-companion/issues/2840)) ([ea4718b](https://github.com/echonuit/vigiechiro-pr-companion/commit/ea4718b7ae22e85b45ba48f2650e42d7879ed973)), closes [#2791](https://github.com/echonuit/vigiechiro-pr-companion/issues/2791) [#2791](https://github.com/echonuit/vigiechiro-pr-companion/issues/2791) [#2790](https://github.com/echonuit/vigiechiro-pr-companion/issues/2790) [#2791](https://github.com/echonuit/vigiechiro-pr-companion/issues/2791) [#2791](https://github.com/echonuit/vigiechiro-pr-companion/issues/2791)

# [2.127.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.126.0...v2.127.0) (2026-07-29)


### Features

* **passage:** fermer « Préparer le dépôt » sur une nuit récupérée ([#2846](https://github.com/echonuit/vigiechiro-pr-companion/issues/2846)) ([7d161c6](https://github.com/echonuit/vigiechiro-pr-companion/commit/7d161c659fd3da71fcf86b2534f917b0888cc178)), closes [#789](https://github.com/echonuit/vigiechiro-pr-companion/issues/789)

# [2.126.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.125.1...v2.126.0) (2026-07-29)


### Features

* **analyse:** la Synthese marque les especes prioritaires ([#2842](https://github.com/echonuit/vigiechiro-pr-companion/issues/2842)) ([91a1f24](https://github.com/echonuit/vigiechiro-pr-companion/commit/91a1f24a7facbb5f2843c25b784a9aa0b0d467e6))

## [2.125.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.125.0...v2.125.1) (2026-07-29)


### Bug Fixes

* **passage:** la fiche d'une nuit rapatriée montrait l'état d'avant, et sa recommandation n'allumait rien ([#2836](https://github.com/echonuit/vigiechiro-pr-companion/issues/2836)) ([8d56e27](https://github.com/echonuit/vigiechiro-pr-companion/commit/8d56e27e70787a71afe445785788779bf467cf0d)), closes [#2581](https://github.com/echonuit/vigiechiro-pr-companion/issues/2581)

# [2.125.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.124.0...v2.125.0) (2026-07-29)


### Features

* **validation:** la commune rejoint la projection audio et l'export CSV (lot 0, exposition) ([#2837](https://github.com/echonuit/vigiechiro-pr-companion/issues/2837)) ([7b5285e](https://github.com/echonuit/vigiechiro-pr-companion/commit/7b5285eb887092f67e0e1ac6579adc40d4a4619c)), closes [#2794](https://github.com/echonuit/vigiechiro-pr-companion/issues/2794)

# [2.124.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.123.0...v2.124.0) (2026-07-29)


### Features

* **cli:** rattraper-communes, et l'inventaire des hotes sortants (lot 0, CLI) ([#2828](https://github.com/echonuit/vigiechiro-pr-companion/issues/2828)) ([f6016a6](https://github.com/echonuit/vigiechiro-pr-companion/commit/f6016a68767f4e8eabdf21acc308f33790f4d55c)), closes [#2791](https://github.com/echonuit/vigiechiro-pr-companion/issues/2791)
* **recherche:** les especes prioritaires portent leur mention ([#2827](https://github.com/echonuit/vigiechiro-pr-companion/issues/2827)) ([b9f9fcc](https://github.com/echonuit/vigiechiro-pr-companion/commit/b9f9fcc93ecc8383e529cf5ef0e59404e9b64795))

# [2.123.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.122.1...v2.123.0) (2026-07-29)


### Features

* **sites:** chaque geste sur un point met sa commune a jour (lot 0, declencheurs) ([#2819](https://github.com/echonuit/vigiechiro-pr-companion/issues/2819)) ([34099f0](https://github.com/echonuit/vigiechiro-pr-companion/commit/34099f0389008001dcaab3d3d69689da0af30864)), closes [#2791](https://github.com/echonuit/vigiechiro-pr-companion/issues/2791) [#2791](https://github.com/echonuit/vigiechiro-pr-companion/issues/2791) [#2791](https://github.com/echonuit/vigiechiro-pr-companion/issues/2791) [#2791](https://github.com/echonuit/vigiechiro-pr-companion/issues/2791)

## [2.122.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.122.0...v2.122.1) (2026-07-29)


### Bug Fixes

* **lot:** la chaîne de dépôt, l'analyse et le solde suivent une nuit récupérée ([#2815](https://github.com/echonuit/vigiechiro-pr-companion/issues/2815)) ([2daf46a](https://github.com/echonuit/vigiechiro-pr-companion/commit/2daf46a0adb1035ec40a41faac8f8f98f1f0a502)), closes [#982](https://github.com/echonuit/vigiechiro-pr-companion/issues/982) [#2581](https://github.com/echonuit/vigiechiro-pr-companion/issues/2581) [#2771](https://github.com/echonuit/vigiechiro-pr-companion/issues/2771)

# [2.122.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.121.0...v2.122.0) (2026-07-29)


### Features

* **passage:** le statut « Récupéré » se voit — stepper, pastille, tri, filtre ([#2809](https://github.com/echonuit/vigiechiro-pr-companion/issues/2809)) ([a0cbb03](https://github.com/echonuit/vigiechiro-pr-companion/commit/a0cbb03f251f170bc3acb117598b7c8392c14e83)), closes [#2581](https://github.com/echonuit/vigiechiro-pr-companion/issues/2581) [#2628](https://github.com/echonuit/vigiechiro-pr-companion/issues/2628)

# [2.121.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.120.0...v2.121.0) (2026-07-29)


### Features

* **sites:** la commune d'un point se derive de son GPS (lot 0, socle) ([#2805](https://github.com/echonuit/vigiechiro-pr-companion/issues/2805)) ([948cf05](https://github.com/echonuit/vigiechiro-pr-companion/commit/948cf05f3924ffbc16f327d9f98c45c73b9f7983)), closes [#2791](https://github.com/echonuit/vigiechiro-pr-companion/issues/2791) [#2483](https://github.com/echonuit/vigiechiro-pr-companion/issues/2483) [#2799](https://github.com/echonuit/vigiechiro-pr-companion/issues/2799) [#2791](https://github.com/echonuit/vigiechiro-pr-companion/issues/2791) [#2791](https://github.com/echonuit/vigiechiro-pr-companion/issues/2791)

# [2.120.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.119.0...v2.120.0) (2026-07-29)


### Features

* **analyse:** exporter la synthese, a l ecran comme en ligne de commande ([#2806](https://github.com/echonuit/vigiechiro-pr-companion/issues/2806)) ([b04ad00](https://github.com/echonuit/vigiechiro-pr-companion/commit/b04ad00a69717223dd46da98c9f877008abcbbcd))

# [2.119.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.118.1...v2.119.0) (2026-07-29)


### Features

* **passage:** le statut « Récupéré » existe, hors de la file linéaire ([#2799](https://github.com/echonuit/vigiechiro-pr-companion/issues/2799)) ([f7f8aa0](https://github.com/echonuit/vigiechiro-pr-companion/commit/f7f8aa022d2fa14ebfdbe5f6db3a62e3c2fe7a66)), closes [#2581](https://github.com/echonuit/vigiechiro-pr-companion/issues/2581)

## [2.118.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.118.0...v2.118.1) (2026-07-29)


### Bug Fixes

* **commun:** un retour d'operation cesse de renvoyer un message d'exception nu ([#2800](https://github.com/echonuit/vigiechiro-pr-companion/issues/2800)) ([3efe1d1](https://github.com/echonuit/vigiechiro-pr-companion/commit/3efe1d19ebb17f0dddcfdbccb35a9cd8145f3d6a)), closes [#2076](https://github.com/echonuit/vigiechiro-pr-companion/issues/2076) [#1845](https://github.com/echonuit/vigiechiro-pr-companion/issues/1845)

# [2.118.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.117.0...v2.118.0) (2026-07-29)


### Features

* **analyse:** l'ecran Synthese de la nuit, ou un nombre trouve sa mesure ([#2797](https://github.com/echonuit/vigiechiro-pr-companion/issues/2797)) ([0730afb](https://github.com/echonuit/vigiechiro-pr-companion/commit/0730afb506017567bea2a7953c09ac95919e0ab6)), closes [#2351](https://github.com/echonuit/vigiechiro-pr-companion/issues/2351) [#2351](https://github.com/echonuit/vigiechiro-pr-companion/issues/2351) [#2348](https://github.com/echonuit/vigiechiro-pr-companion/issues/2348) [#2351](https://github.com/echonuit/vigiechiro-pr-companion/issues/2351)

# [2.117.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.116.3...v2.117.0) (2026-07-29)


### Features

* **passage:** une nuit récupérée n'annule pas un dépôt qu'elle n'a pas fait ([#2786](https://github.com/echonuit/vigiechiro-pr-companion/issues/2786)) ([e0f9670](https://github.com/echonuit/vigiechiro-pr-companion/commit/e0f9670e548e0d126cb2146b808c1f04c58ca7a4)), closes [#2760](https://github.com/echonuit/vigiechiro-pr-companion/issues/2760) [#2581](https://github.com/echonuit/vigiechiro-pr-companion/issues/2581) [#789](https://github.com/echonuit/vigiechiro-pr-companion/issues/789)

## [2.116.3](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.116.2...v2.116.3) (2026-07-29)


### Bug Fixes

* **tests:** le cliquet de fixtures cesse d'être aveugle et de surcompter ([#2782](https://github.com/echonuit/vigiechiro-pr-companion/issues/2782)) ([f5724a2](https://github.com/echonuit/vigiechiro-pr-companion/commit/f5724a230152786dca8efdfd075a74e236b56d36)), closes [#2714](https://github.com/echonuit/vigiechiro-pr-companion/issues/2714)

## [2.116.2](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.116.1...v2.116.2) (2026-07-29)


### Bug Fixes

* **ci:** le minion de PIT recoit les butoirs TestFX, et un test le tient ([#2780](https://github.com/echonuit/vigiechiro-pr-companion/issues/2780)) ([38ae1fc](https://github.com/echonuit/vigiechiro-pr-companion/commit/38ae1fcedae8cc5571312963ed3ed6b6ea3db696)), closes [#2120](https://github.com/echonuit/vigiechiro-pr-companion/issues/2120)

## [2.116.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.116.0...v2.116.1) (2026-07-29)


### Bug Fixes

* **sites:** la synchro cesse d'effacer ce qui a abouti, et dit sa part ([#2761](https://github.com/echonuit/vigiechiro-pr-companion/issues/2761)) ([6fbf65b](https://github.com/echonuit/vigiechiro-pr-companion/commit/6fbf65ba1851c2de0e2273af20894d6dc80e9c58)), closes [#2655](https://github.com/echonuit/vigiechiro-pr-companion/issues/2655) [#2655](https://github.com/echonuit/vigiechiro-pr-companion/issues/2655) [#2677](https://github.com/echonuit/vigiechiro-pr-companion/issues/2677) [#2695](https://github.com/echonuit/vigiechiro-pr-companion/issues/2695) [#2655](https://github.com/echonuit/vigiechiro-pr-companion/issues/2655)

# [2.116.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.115.0...v2.116.0) (2026-07-29)


### Features

* **analyse:** la synthese d'une nuit, espece par espece ([#2779](https://github.com/echonuit/vigiechiro-pr-companion/issues/2779)) ([67d2293](https://github.com/echonuit/vigiechiro-pr-companion/commit/67d2293b81cb6bf4f07f62ac65fac0d901a1d9c8)), closes [#2351](https://github.com/echonuit/vigiechiro-pr-companion/issues/2351) [#2351](https://github.com/echonuit/vigiechiro-pr-companion/issues/2351) [#2348](https://github.com/echonuit/vigiechiro-pr-companion/issues/2348)

# [2.115.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.114.0...v2.115.0) (2026-07-29)


### Features

* **commun:** un nombre de contacts peut enfin se lire contre un referentiel ([#2764](https://github.com/echonuit/vigiechiro-pr-companion/issues/2764)) ([573f009](https://github.com/echonuit/vigiechiro-pr-companion/commit/573f009c53d8eb299ceda70e4ea771be62c09201)), closes [#2351](https://github.com/echonuit/vigiechiro-pr-companion/issues/2351) [#2351](https://github.com/echonuit/vigiechiro-pr-companion/issues/2351) [#2348](https://github.com/echonuit/vigiechiro-pr-companion/issues/2348) [#2351](https://github.com/echonuit/vigiechiro-pr-companion/issues/2351)

# [2.114.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.113.1...v2.114.0) (2026-07-29)


### Features

* **passage:** supprimer une nuit récupérée sans passer par un dépôt qu'on n'a pas fait ([#2760](https://github.com/echonuit/vigiechiro-pr-companion/issues/2760)) ([53fe0f9](https://github.com/echonuit/vigiechiro-pr-companion/commit/53fe0f965c2dd88cf01c75ea72bbed7e0668948c)), closes [#2581](https://github.com/echonuit/vigiechiro-pr-companion/issues/2581) [#789](https://github.com/echonuit/vigiechiro-pr-companion/issues/789)

## [2.113.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.113.0...v2.113.1) (2026-07-28)


### Bug Fixes

* **commun:** le libelle d'une ventilation devient le texte accessible de sa barre ([#2719](https://github.com/echonuit/vigiechiro-pr-companion/issues/2719)) ([3af5c5a](https://github.com/echonuit/vigiechiro-pr-companion/commit/3af5c5ac7c8fc0f9c1a79652da6b3dfb0575fba1)), closes [#2694](https://github.com/echonuit/vigiechiro-pr-companion/issues/2694)

# [2.113.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.112.1...v2.113.0) (2026-07-28)


### Features

* **import:** router vers la réactivation quand la nuit est déjà récupérée ([#2711](https://github.com/echonuit/vigiechiro-pr-companion/issues/2711)) ([fdc8d75](https://github.com/echonuit/vigiechiro-pr-companion/commit/fdc8d75a96cb27a840fc92fbc2b26dba3c5e3650)), closes [#2557](https://github.com/echonuit/vigiechiro-pr-companion/issues/2557) [#2580](https://github.com/echonuit/vigiechiro-pr-companion/issues/2580) [#147](https://github.com/echonuit/vigiechiro-pr-companion/issues/147) [#107](https://github.com/echonuit/vigiechiro-pr-companion/issues/107) [#2580](https://github.com/echonuit/vigiechiro-pr-companion/issues/2580)

## [2.112.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.112.0...v2.112.1) (2026-07-28)


### Bug Fixes

* **api:** « Annuler » cesse de disparaitre pendant une temporisation de reprise ([#2712](https://github.com/echonuit/vigiechiro-pr-companion/issues/2712)) ([853c38e](https://github.com/echonuit/vigiechiro-pr-companion/commit/853c38e62389e2db56b6e66aa3274ff6618e896a)), closes [#2686](https://github.com/echonuit/vigiechiro-pr-companion/issues/2686) [#2619](https://github.com/echonuit/vigiechiro-pr-companion/issues/2619) [#1522](https://github.com/echonuit/vigiechiro-pr-companion/issues/1522) [#2686](https://github.com/echonuit/vigiechiro-pr-companion/issues/2686) [#2354](https://github.com/echonuit/vigiechiro-pr-companion/issues/2354)

# [2.112.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.111.0...v2.112.0) (2026-07-28)


### Features

* **revue:** compter ce qui reste a revoir parmi les especes a enjeu ([#2707](https://github.com/echonuit/vigiechiro-pr-companion/issues/2707)) ([334328a](https://github.com/echonuit/vigiechiro-pr-companion/commit/334328a0386ba9cc1a42577b0319d9ad05ef0304)), closes [#2353](https://github.com/echonuit/vigiechiro-pr-companion/issues/2353) [#2353](https://github.com/echonuit/vigiechiro-pr-companion/issues/2353) [#2353](https://github.com/echonuit/vigiechiro-pr-companion/issues/2353) [#2353](https://github.com/echonuit/vigiechiro-pr-companion/issues/2353) [#2348](https://github.com/echonuit/vigiechiro-pr-companion/issues/2348)

# [2.111.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.110.0...v2.111.0) (2026-07-28)


### Features

* **lot:** la fin de depot rend ses comptes en chiffres ([#2708](https://github.com/echonuit/vigiechiro-pr-companion/issues/2708)) ([511d105](https://github.com/echonuit/vigiechiro-pr-companion/commit/511d105c0971375e79bc56348e93054a541fe985)), closes [#2653](https://github.com/echonuit/vigiechiro-pr-companion/issues/2653) [#2586](https://github.com/echonuit/vigiechiro-pr-companion/issues/2586) [#2653](https://github.com/echonuit/vigiechiro-pr-companion/issues/2653) [#2350](https://github.com/echonuit/vigiechiro-pr-companion/issues/2350) [#1044](https://github.com/echonuit/vigiechiro-pr-companion/issues/1044) [#2653](https://github.com/echonuit/vigiechiro-pr-companion/issues/2653) [#1890](https://github.com/echonuit/vigiechiro-pr-companion/issues/1890) [#2653](https://github.com/echonuit/vigiechiro-pr-companion/issues/2653) [#2350](https://github.com/echonuit/vigiechiro-pr-companion/issues/2350)
* **passage:** ne demander copier ou referencer que la ou ca compte ([#2704](https://github.com/echonuit/vigiechiro-pr-companion/issues/2704)) ([a0466e7](https://github.com/echonuit/vigiechiro-pr-companion/commit/a0466e7ad232e62a3fda7303635a668eb67a28f3)), closes [#2554](https://github.com/echonuit/vigiechiro-pr-companion/issues/2554) [#2577](https://github.com/echonuit/vigiechiro-pr-companion/issues/2577) [#2642](https://github.com/echonuit/vigiechiro-pr-companion/issues/2642)

# [2.110.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.109.0...v2.110.0) (2026-07-28)


### Features

* **lot:** déclencher le calcul de plusieurs nuits ([#2703](https://github.com/echonuit/vigiechiro-pr-companion/issues/2703)) ([5874c2c](https://github.com/echonuit/vigiechiro-pr-companion/commit/5874c2c6315a62bf060e88fc7d63014007befd6d)), closes [#2357](https://github.com/echonuit/vigiechiro-pr-companion/issues/2357)

# [2.109.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.108.0...v2.109.0) (2026-07-28)


### Features

* **analyse:** les especes a enjeu se reperent et s'isolent dans les vues agregees ([#2697](https://github.com/echonuit/vigiechiro-pr-companion/issues/2697)) ([5c4f42b](https://github.com/echonuit/vigiechiro-pr-companion/commit/5c4f42ba364ca8e43a8c1273234e85701a886d27)), closes [#2353](https://github.com/echonuit/vigiechiro-pr-companion/issues/2353) [#2353](https://github.com/echonuit/vigiechiro-pr-companion/issues/2353) [#2348](https://github.com/echonuit/vigiechiro-pr-companion/issues/2348) [#2353](https://github.com/echonuit/vigiechiro-pr-companion/issues/2353)

# [2.108.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.107.0...v2.108.0) (2026-07-28)


### Features

* **validation:** importer les résultats de plusieurs nuits ([#2698](https://github.com/echonuit/vigiechiro-pr-companion/issues/2698)) ([6ee9eae](https://github.com/echonuit/vigiechiro-pr-companion/commit/6ee9eaec29e8aa05b0a95c5256065c428a52e9d5)), closes [#2357](https://github.com/echonuit/vigiechiro-pr-companion/issues/2357) [#1264](https://github.com/echonuit/vigiechiro-pr-companion/issues/1264) [#2483](https://github.com/echonuit/vigiechiro-pr-companion/issues/2483)

# [2.107.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.106.0...v2.107.0) (2026-07-28)


### Features

* **audio:** la fin d'import Vigie-Chiro rend ses comptes en chiffres ([#2695](https://github.com/echonuit/vigiechiro-pr-companion/issues/2695)) ([c90ec87](https://github.com/echonuit/vigiechiro-pr-companion/commit/c90ec8763ba3ab6a94ccefef5ca1a43e0bfa229b)), closes [#2651](https://github.com/echonuit/vigiechiro-pr-companion/issues/2651) [#2677](https://github.com/echonuit/vigiechiro-pr-companion/issues/2677) [#2651](https://github.com/echonuit/vigiechiro-pr-companion/issues/2651) [#2651](https://github.com/echonuit/vigiechiro-pr-companion/issues/2651) [#2694](https://github.com/echonuit/vigiechiro-pr-companion/issues/2694) [#2350](https://github.com/echonuit/vigiechiro-pr-companion/issues/2350)

# [2.106.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.105.0...v2.106.0) (2026-07-28)


### Features

* **lot:** téléverser plusieurs nuits d'affilée ([#2691](https://github.com/echonuit/vigiechiro-pr-companion/issues/2691)) ([1a6a6c0](https://github.com/echonuit/vigiechiro-pr-companion/commit/1a6a6c06b2833257061595a798f51474c2ecb18c)), closes [#2357](https://github.com/echonuit/vigiechiro-pr-companion/issues/2357) [#2483](https://github.com/echonuit/vigiechiro-pr-companion/issues/2483) [#2669](https://github.com/echonuit/vigiechiro-pr-companion/issues/2669)

# [2.105.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.104.0...v2.105.0) (2026-07-28)


### Features

* **analyse:** l'activite de la nuit s'ouvre la ou le protocole regarde ([#2690](https://github.com/echonuit/vigiechiro-pr-companion/issues/2690)) ([bdc1aa9](https://github.com/echonuit/vigiechiro-pr-companion/commit/bdc1aa9fbefc6230942b3ade97ab9c99ad082a40)), closes [#2616](https://github.com/echonuit/vigiechiro-pr-companion/issues/2616) [#2616](https://github.com/echonuit/vigiechiro-pr-companion/issues/2616)

# [2.104.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.103.0...v2.104.0) (2026-07-28)


### Features

* **commun:** un refus porte ce qui manque, la surface ajoute le geste ([#2685](https://github.com/echonuit/vigiechiro-pr-companion/issues/2685)) ([6d23118](https://github.com/echonuit/vigiechiro-pr-companion/commit/6d2311872f807c0945aaa8f414f5f5800e729dcc)), closes [#2554](https://github.com/echonuit/vigiechiro-pr-companion/issues/2554) [#2635](https://github.com/echonuit/vigiechiro-pr-companion/issues/2635)

# [2.103.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.102.0...v2.103.0) (2026-07-28)


### Features

* **audio:** les especes a enjeu se reperent et s'isolent dans la revue ([#2675](https://github.com/echonuit/vigiechiro-pr-companion/issues/2675)) ([2019cea](https://github.com/echonuit/vigiechiro-pr-companion/commit/2019ceae87d789aeec1fab0012a1ded00b5c9703)), closes [#2353](https://github.com/echonuit/vigiechiro-pr-companion/issues/2353) [#2353](https://github.com/echonuit/vigiechiro-pr-companion/issues/2353) [#2348](https://github.com/echonuit/vigiechiro-pr-companion/issues/2348) [#2353](https://github.com/echonuit/vigiechiro-pr-companion/issues/2353) [#547](https://github.com/echonuit/vigiechiro-pr-companion/issues/547)

# [2.102.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.101.2...v2.102.0) (2026-07-28)


### Features

* **multisite:** cocher plusieurs passages et leur préparer le dépôt ([#2679](https://github.com/echonuit/vigiechiro-pr-companion/issues/2679)) ([2619483](https://github.com/echonuit/vigiechiro-pr-companion/commit/26194832887c63bb86e2245cc286cc4f4dc75514)), closes [#2357](https://github.com/echonuit/vigiechiro-pr-companion/issues/2357) [#2357](https://github.com/echonuit/vigiechiro-pr-companion/issues/2357) [#2669](https://github.com/echonuit/vigiechiro-pr-companion/issues/2669)

## [2.101.2](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.101.1...v2.101.2) (2026-07-28)


### Bug Fixes

* **api:** une ecriture qui cree n'est plus rejouee sur une coupure ([#2680](https://github.com/echonuit/vigiechiro-pr-companion/issues/2680)) ([d2abb1f](https://github.com/echonuit/vigiechiro-pr-companion/commit/d2abb1f2218126ba81d2cc77f4f4c3d00b620942)), closes [#2677](https://github.com/echonuit/vigiechiro-pr-companion/issues/2677) [#2672](https://github.com/echonuit/vigiechiro-pr-companion/issues/2672) [#2350](https://github.com/echonuit/vigiechiro-pr-companion/issues/2350) [#2672](https://github.com/echonuit/vigiechiro-pr-companion/issues/2672) [#2677](https://github.com/echonuit/vigiechiro-pr-companion/issues/2677)

## [2.101.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.101.0...v2.101.1) (2026-07-28)


### Bug Fixes

* **api:** le reessai gradue couvre aussi les lectures ([#2672](https://github.com/echonuit/vigiechiro-pr-companion/issues/2672)) ([6a65083](https://github.com/echonuit/vigiechiro-pr-companion/commit/6a6508339094f3c7cfdedf0e2b59975ed4f5f1f6)), closes [#2354](https://github.com/echonuit/vigiechiro-pr-companion/issues/2354) [#1338](https://github.com/echonuit/vigiechiro-pr-companion/issues/1338) [#2619](https://github.com/echonuit/vigiechiro-pr-companion/issues/2619)

# [2.101.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.100.0...v2.101.0) (2026-07-28)


### Features

* **multisite:** la releve des analyses sort du voile opaque ([#2670](https://github.com/echonuit/vigiechiro-pr-companion/issues/2670)) ([9fecb48](https://github.com/echonuit/vigiechiro-pr-companion/commit/9fecb48a0c07715d167bd6477d8cb7828a821107)), closes [#2642](https://github.com/echonuit/vigiechiro-pr-companion/issues/2642) [#2636](https://github.com/echonuit/vigiechiro-pr-companion/issues/2636)

# [2.100.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.99.0...v2.100.0) (2026-07-28)


### Features

* **validation:** le produit sait enfin quelles especes sont a enjeu ([#2666](https://github.com/echonuit/vigiechiro-pr-companion/issues/2666)) ([457c300](https://github.com/echonuit/vigiechiro-pr-companion/commit/457c3008a1ab67a4cf26481c9cdc82fc4e424cb6)), closes [#2353](https://github.com/echonuit/vigiechiro-pr-companion/issues/2353) [#2353](https://github.com/echonuit/vigiechiro-pr-companion/issues/2353) [#2348](https://github.com/echonuit/vigiechiro-pr-companion/issues/2348)

# [2.99.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.98.0...v2.99.0) (2026-07-28)


### Features

* **saison:** filtrer le solde par campagne depuis l'écran ([#2665](https://github.com/echonuit/vigiechiro-pr-companion/issues/2665)) ([c4768ef](https://github.com/echonuit/vigiechiro-pr-companion/commit/c4768ef391fe23fb765297905b80d6186d5b2019)), closes [#2610](https://github.com/echonuit/vigiechiro-pr-companion/issues/2610) [#2356](https://github.com/echonuit/vigiechiro-pr-companion/issues/2356) [#2349](https://github.com/echonuit/vigiechiro-pr-companion/issues/2349)

# [2.98.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.97.0...v2.98.0) (2026-07-28)


### Features

* **connexion:** l avancement parait dans la modale, plus dans une seconde fenetre ([#2664](https://github.com/echonuit/vigiechiro-pr-companion/issues/2664)) ([96d7458](https://github.com/echonuit/vigiechiro-pr-companion/commit/96d745856ee0048ba023ef0b25379443b3c9a3a0)), closes [#2642](https://github.com/echonuit/vigiechiro-pr-companion/issues/2642) [#2642](https://github.com/echonuit/vigiechiro-pr-companion/issues/2642)

# [2.97.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.96.0...v2.97.0) (2026-07-28)


### Features

* **analyse:** une nuit realisee sur le carre d'un tiers ne se fond plus dans le lot ([#2662](https://github.com/echonuit/vigiechiro-pr-companion/issues/2662)) ([fcd6b0e](https://github.com/echonuit/vigiechiro-pr-companion/commit/fcd6b0e9fea56880041d8ef2080d0704de1800d0)), closes [#2614](https://github.com/echonuit/vigiechiro-pr-companion/issues/2614) [#2614](https://github.com/echonuit/vigiechiro-pr-companion/issues/2614)

# [2.96.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.95.0...v2.96.0) (2026-07-28)


### Features

* **import:** proposer la campagne du dernier passage du même point ([#2646](https://github.com/echonuit/vigiechiro-pr-companion/issues/2646)) ([752aa1f](https://github.com/echonuit/vigiechiro-pr-companion/commit/752aa1f4aa724913bad2481f9d134ada6dff7c98)), closes [#2525](https://github.com/echonuit/vigiechiro-pr-companion/issues/2525) [#2631](https://github.com/echonuit/vigiechiro-pr-companion/issues/2631) [#2355](https://github.com/echonuit/vigiechiro-pr-companion/issues/2355) [#2349](https://github.com/echonuit/vigiechiro-pr-companion/issues/2349)

# [2.95.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.94.1...v2.95.0) (2026-07-28)


### Features

* **campagne:** gérer les campagnes depuis l'application ([#2640](https://github.com/echonuit/vigiechiro-pr-companion/issues/2640)) ([348b13b](https://github.com/echonuit/vigiechiro-pr-companion/commit/348b13bc641a58557c76a81c5f2d78f0366410a1)), closes [#2630](https://github.com/echonuit/vigiechiro-pr-companion/issues/2630) [#2630](https://github.com/echonuit/vigiechiro-pr-companion/issues/2630) [#2355](https://github.com/echonuit/vigiechiro-pr-companion/issues/2355) [#2355](https://github.com/echonuit/vigiechiro-pr-companion/issues/2355) [#2630](https://github.com/echonuit/vigiechiro-pr-companion/issues/2630) [#2630](https://github.com/echonuit/vigiechiro-pr-companion/issues/2630)

## [2.94.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.94.0...v2.94.1) (2026-07-28)


### Bug Fixes

* **passage:** retrouver la nuit d une participation par une lecture indexee ([#2650](https://github.com/echonuit/vigiechiro-pr-companion/issues/2650)) ([6c97606](https://github.com/echonuit/vigiechiro-pr-companion/commit/6c976065d3390ab65bbf09636dbc5aa2f2ac5db7)), closes [#2554](https://github.com/echonuit/vigiechiro-pr-companion/issues/2554) [#2638](https://github.com/echonuit/vigiechiro-pr-companion/issues/2638)

# [2.94.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.93.0...v2.94.0) (2026-07-28)


### Features

* **commun:** une puce de filtre qui retient plusieurs valeurs, et un onglet « Autres » qui tient sa promesse ([#2647](https://github.com/echonuit/vigiechiro-pr-companion/issues/2647)) ([77e6e64](https://github.com/echonuit/vigiechiro-pr-companion/commit/77e6e64398cca5e69dad94b5cb443656e1664380)), closes [#2615](https://github.com/echonuit/vigiechiro-pr-companion/issues/2615) [#2615](https://github.com/echonuit/vigiechiro-pr-companion/issues/2615)

# [2.93.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.92.1...v2.93.0) (2026-07-28)


### Features

* **diagnostic:** la nuit réelle sur la courbe climatique, et son export ([#2641](https://github.com/echonuit/vigiechiro-pr-companion/issues/2641)) ([d553ce8](https://github.com/echonuit/vigiechiro-pr-companion/commit/d553ce8d0fa34a7979eb3dd64a14c07cd7e95c59)), closes [#2617](https://github.com/echonuit/vigiechiro-pr-companion/issues/2617) [#2618](https://github.com/echonuit/vigiechiro-pr-companion/issues/2618) [#2617](https://github.com/echonuit/vigiechiro-pr-companion/issues/2617) [#2618](https://github.com/echonuit/vigiechiro-pr-companion/issues/2618)

## [2.92.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.92.0...v2.92.1) (2026-07-28)


### Bug Fixes

* **passage:** completer une nuit ne la detruit plus pour la refaire ([#2637](https://github.com/echonuit/vigiechiro-pr-companion/issues/2637)) ([6c9490a](https://github.com/echonuit/vigiechiro-pr-companion/commit/6c9490a2b6a784866db7251cd027a06a7dabc40b)), closes [#1892](https://github.com/echonuit/vigiechiro-pr-companion/issues/1892) [#1828](https://github.com/echonuit/vigiechiro-pr-companion/issues/1828) [#1688](https://github.com/echonuit/vigiechiro-pr-companion/issues/1688) [#2554](https://github.com/echonuit/vigiechiro-pr-companion/issues/2554)

# [2.92.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.91.0...v2.92.0) (2026-07-28)


### Features

* **analyse:** un export d'activité qui dit d'où viennent ses lignes ([#2627](https://github.com/echonuit/vigiechiro-pr-companion/issues/2627)) ([b41ac57](https://github.com/echonuit/vigiechiro-pr-companion/commit/b41ac57d0c2eb0ef42fab4ba29f7bad23d8abbf0)), closes [#2613](https://github.com/echonuit/vigiechiro-pr-companion/issues/2613) [#2613](https://github.com/echonuit/vigiechiro-pr-companion/issues/2613)

# [2.91.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.90.0...v2.91.0) (2026-07-28)


### Features

* **audio:** la publication des corrections rend des comptes en chiffres ([#2358](https://github.com/echonuit/vigiechiro-pr-companion/issues/2358)) ([#2629](https://github.com/echonuit/vigiechiro-pr-companion/issues/2629)) ([028213e](https://github.com/echonuit/vigiechiro-pr-companion/commit/028213e9389531e5ab32bed40aaa36e90f136ba8))

# [2.90.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.89.0...v2.90.0) (2026-07-28)


### Features

* **multisite:** la revue visuelle rattrape un renommage a moitie fait, et montre trois etats invisibles ([#2626](https://github.com/echonuit/vigiechiro-pr-companion/issues/2626)) ([9dbbbcd](https://github.com/echonuit/vigiechiro-pr-companion/commit/9dbbbcdaf6286b52e8fa45a7ea867aaa876b8066)), closes [#2554](https://github.com/echonuit/vigiechiro-pr-companion/issues/2554)

# [2.89.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.88.0...v2.89.0) (2026-07-27)


### Features

* **passage:** la réactivation rend des comptes en chiffres ([#2358](https://github.com/echonuit/vigiechiro-pr-companion/issues/2358)) ([#2623](https://github.com/echonuit/vigiechiro-pr-companion/issues/2623)) ([15c6833](https://github.com/echonuit/vigiechiro-pr-companion/commit/15c68330c95ddbc7579c33c059a1cac1ca988ca7))

# [2.88.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.87.1...v2.88.0) (2026-07-27)


### Bug Fixes

* **synchro:** le compte rendu ventile par cause, et les libellés cessent de mentir ([#2608](https://github.com/echonuit/vigiechiro-pr-companion/issues/2608)) ([40ab50b](https://github.com/echonuit/vigiechiro-pr-companion/commit/40ab50b6e05b3755c6c6a8ee2222ee7f517fcebe)), closes [#2554](https://github.com/echonuit/vigiechiro-pr-companion/issues/2554) [#2280](https://github.com/echonuit/vigiechiro-pr-companion/issues/2280) [#1565](https://github.com/echonuit/vigiechiro-pr-companion/issues/1565) [#2557](https://github.com/echonuit/vigiechiro-pr-companion/issues/2557) [#2557](https://github.com/echonuit/vigiechiro-pr-companion/issues/2557) [#2554](https://github.com/echonuit/vigiechiro-pr-companion/issues/2554) [#2605](https://github.com/echonuit/vigiechiro-pr-companion/issues/2605)


### Features

* **analyse:** les mêmes onglets de catégories sur Espèces & observations ([#2612](https://github.com/echonuit/vigiechiro-pr-companion/issues/2612)) ([d5d446b](https://github.com/echonuit/vigiechiro-pr-companion/commit/d5d446b73c89b98d8cd6ccd1b0617a92a6781031)), closes [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352)

## [2.87.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.87.0...v2.87.1) (2026-07-27)


### Bug Fixes

* **importation:** le compte rendu rappelle ce qui reste vrai et dit l'écriture distante ([#1488](https://github.com/echonuit/vigiechiro-pr-companion/issues/1488)) ([#2602](https://github.com/echonuit/vigiechiro-pr-companion/issues/2602)) ([e3f068b](https://github.com/echonuit/vigiechiro-pr-companion/commit/e3f068b91e5e1bede63c7415dc44e26c19c2da62)), closes [#2483](https://github.com/echonuit/vigiechiro-pr-companion/issues/2483)

# [2.87.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.86.0...v2.87.0) (2026-07-27)


### Features

* **importation:** la fin d'import rend des comptes en chiffres ([#2358](https://github.com/echonuit/vigiechiro-pr-companion/issues/2358)) ([#2600](https://github.com/echonuit/vigiechiro-pr-companion/issues/2600)) ([45dc912](https://github.com/echonuit/vigiechiro-pr-companion/commit/45dc91268cdb20da80ad46101a1c8834d7b20e63)), closes [#2004](https://github.com/echonuit/vigiechiro-pr-companion/issues/2004) [#155](https://github.com/echonuit/vigiechiro-pr-companion/issues/155) [#1486](https://github.com/echonuit/vigiechiro-pr-companion/issues/1486) [#2050](https://github.com/echonuit/vigiechiro-pr-companion/issues/2050)

# [2.86.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.85.0...v2.86.0) (2026-07-27)


### Features

* **analyse:** des onglets pour isoler les catégories de taxons sur la courbe d'activité ([#2598](https://github.com/echonuit/vigiechiro-pr-companion/issues/2598)) ([aedb621](https://github.com/echonuit/vigiechiro-pr-companion/commit/aedb6210d9bb3e63b3830345f29ce73f2bed8f05)), closes [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#623](https://github.com/echonuit/vigiechiro-pr-companion/issues/623) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352)

# [2.85.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.84.0...v2.85.0) (2026-07-27)


### Features

* **importation:** un import se traduit en compte rendu chiffré ([#2358](https://github.com/echonuit/vigiechiro-pr-companion/issues/2358)) ([#2595](https://github.com/echonuit/vigiechiro-pr-companion/issues/2595)) ([6b0854c](https://github.com/echonuit/vigiechiro-pr-companion/commit/6b0854c2e19259c4c312ef09271dd9aae1f1396d)), closes [#155](https://github.com/echonuit/vigiechiro-pr-companion/issues/155) [#2586](https://github.com/echonuit/vigiechiro-pr-companion/issues/2586) [#2076](https://github.com/echonuit/vigiechiro-pr-companion/issues/2076)

# [2.84.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.83.0...v2.84.0) (2026-07-27)


### Features

* **commun:** chaque motif de rejet ouvre la liste de ses fichiers ([#2358](https://github.com/echonuit/vigiechiro-pr-companion/issues/2358)) ([#2594](https://github.com/echonuit/vigiechiro-pr-companion/issues/2594)) ([4b4f0bb](https://github.com/echonuit/vigiechiro-pr-companion/commit/4b4f0bbe4e552cc21a246f6fd5f8424819c43d67)), closes [#2574](https://github.com/echonuit/vigiechiro-pr-companion/issues/2574) [#1486](https://github.com/echonuit/vigiechiro-pr-companion/issues/1486)

# [2.83.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.82.0...v2.83.0) (2026-07-27)


### Features

* **commun:** moteur de traitement groupé des passages ([#2357](https://github.com/echonuit/vigiechiro-pr-companion/issues/2357), lot 3, PR 1/5) ([#2593](https://github.com/echonuit/vigiechiro-pr-companion/issues/2593)) ([b7a0a8e](https://github.com/echonuit/vigiechiro-pr-companion/commit/b7a0a8e63c82707ec2f399f32e4c9c8dc13c94ad))

# [2.82.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.81.0...v2.82.0) (2026-07-27)


### Features

* **analyse:** l'Activité de la nuit devient une fonctionnalité offerte, documentée et illustrée ([#2569](https://github.com/echonuit/vigiechiro-pr-companion/issues/2569)) ([98e084d](https://github.com/echonuit/vigiechiro-pr-companion/commit/98e084daf75c287b0869c4b60256c0afb5c6d0b6)), closes [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2348](https://github.com/echonuit/vigiechiro-pr-companion/issues/2348) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#1878](https://github.com/echonuit/vigiechiro-pr-companion/issues/1878)

# [2.81.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.80.1...v2.81.0) (2026-07-27)


### Features

* **synchro:** rendre la récupération des nuits suivable et interruptible ([#2585](https://github.com/echonuit/vigiechiro-pr-companion/issues/2585)) ([10b4c38](https://github.com/echonuit/vigiechiro-pr-companion/commit/10b4c38c7b96e8ad3b9dd598c4250daab4417c57)), closes [#2557](https://github.com/echonuit/vigiechiro-pr-companion/issues/2557) [#2558](https://github.com/echonuit/vigiechiro-pr-companion/issues/2558) [#1369](https://github.com/echonuit/vigiechiro-pr-companion/issues/1369)

## [2.80.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.80.0...v2.80.1) (2026-07-27)


### Bug Fixes

* **commun:** la bande chiffrée cesse d'habiller tous les comptes rendus textuels ([#2358](https://github.com/echonuit/vigiechiro-pr-companion/issues/2358)) ([#2588](https://github.com/echonuit/vigiechiro-pr-companion/issues/2588)) ([4bde86c](https://github.com/echonuit/vigiechiro-pr-companion/commit/4bde86c11adf21b5a9343a74423e67c64a43eceb)), closes [#2574](https://github.com/echonuit/vigiechiro-pr-companion/issues/2574) [#2574](https://github.com/echonuit/vigiechiro-pr-companion/issues/2574)

# [2.80.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.79.0...v2.80.0) (2026-07-27)


### Features

* **importation:** le moteur mesure ce qu'il a lu sur la carte ([#2358](https://github.com/echonuit/vigiechiro-pr-companion/issues/2358)) ([#2586](https://github.com/echonuit/vigiechiro-pr-companion/issues/2586)) ([2368a86](https://github.com/echonuit/vigiechiro-pr-companion/commit/2368a863eef4d7c835d99c77db9415556effd855)), closes [#2041](https://github.com/echonuit/vigiechiro-pr-companion/issues/2041) [#2483](https://github.com/echonuit/vigiechiro-pr-companion/issues/2483)

# [2.79.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.78.0...v2.79.0) (2026-07-27)


### Features

* **saison:** filtrer le solde par campagne ([#2355](https://github.com/echonuit/vigiechiro-pr-companion/issues/2355)) ([#2582](https://github.com/echonuit/vigiechiro-pr-companion/issues/2582)) ([cbdcb35](https://github.com/echonuit/vigiechiro-pr-companion/commit/cbdcb35371566c128cb15c3afc44cf89739c43dd)), closes [#2349](https://github.com/echonuit/vigiechiro-pr-companion/issues/2349)

# [2.78.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.77.0...v2.78.0) (2026-07-27)


### Features

* **commun:** le compte rendu chiffré a sa surface, largeurs liées aux quantités ([#2358](https://github.com/echonuit/vigiechiro-pr-companion/issues/2358)) ([#2574](https://github.com/echonuit/vigiechiro-pr-companion/issues/2574)) ([52a48d1](https://github.com/echonuit/vigiechiro-pr-companion/commit/52a48d1bdd8cc171dcaee0f22e7d73ab3d7e33b3))

# [2.77.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.76.0...v2.77.0) (2026-07-27)


### Features

* **multisite:** filtrer les passages par campagne ([#2355](https://github.com/echonuit/vigiechiro-pr-companion/issues/2355)) ([#2579](https://github.com/echonuit/vigiechiro-pr-companion/issues/2579)) ([2f7e4ca](https://github.com/echonuit/vigiechiro-pr-companion/commit/2f7e4cab6c37ec8da721bd9cba01d4ec91930369)), closes [#2572](https://github.com/echonuit/vigiechiro-pr-companion/issues/2572) [#2576](https://github.com/echonuit/vigiechiro-pr-companion/issues/2576)

# [2.76.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.75.0...v2.76.0) (2026-07-27)


### Features

* **multisite:** colonne « Campagne » dans le tableau et tri par campagne ([#2355](https://github.com/echonuit/vigiechiro-pr-companion/issues/2355)) ([#2576](https://github.com/echonuit/vigiechiro-pr-companion/issues/2576)) ([60b8788](https://github.com/echonuit/vigiechiro-pr-companion/commit/60b87887794e0edde54c0c01f5eb3592ccd1338b)), closes [#2572](https://github.com/echonuit/vigiechiro-pr-companion/issues/2572)
* **passage:** la synchro amene chaque nuit au niveau contenu ([#2571](https://github.com/echonuit/vigiechiro-pr-companion/issues/2571)) ([a1141fd](https://github.com/echonuit/vigiechiro-pr-companion/commit/a1141fd67af879adda49756c7562247cb571a04f)), closes [#1814](https://github.com/echonuit/vigiechiro-pr-companion/issues/1814) [#1814](https://github.com/echonuit/vigiechiro-pr-companion/issues/1814) [#2557](https://github.com/echonuit/vigiechiro-pr-companion/issues/2557)

# [2.75.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.74.0...v2.75.0) (2026-07-26)


### Features

* **multisite:** porter la campagne dans la vue agrégée et son export CSV ([#2355](https://github.com/echonuit/vigiechiro-pr-companion/issues/2355), lot 1) ([#2572](https://github.com/echonuit/vigiechiro-pr-companion/issues/2572)) ([84a8a90](https://github.com/echonuit/vigiechiro-pr-companion/commit/84a8a90befe5e4b42612309358d500506c7df266)), closes [#2529](https://github.com/echonuit/vigiechiro-pr-companion/issues/2529) [#1338](https://github.com/echonuit/vigiechiro-pr-companion/issues/1338)

# [2.74.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.73.0...v2.74.0) (2026-07-26)


### Features

* **commun:** modèle du compte rendu chiffré, ses règles tenues par le type ([#2358](https://github.com/echonuit/vigiechiro-pr-companion/issues/2358)) ([#2567](https://github.com/echonuit/vigiechiro-pr-companion/issues/2567)) ([3465f80](https://github.com/echonuit/vigiechiro-pr-companion/commit/3465f80437f6d482e5603f53a2defe69615d6b35))

# [2.73.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.72.0...v2.73.0) (2026-07-26)


### Features

* **sites:** marquer les nuits des carrés de tiers et les écarter du solde ([#2525](https://github.com/echonuit/vigiechiro-pr-companion/issues/2525)) ([#2566](https://github.com/echonuit/vigiechiro-pr-companion/issues/2566)) ([1732a0c](https://github.com/echonuit/vigiechiro-pr-companion/commit/1732a0cc4883c52963e7b85aeabe4f1471ea190b)), closes [#2552](https://github.com/echonuit/vigiechiro-pr-companion/issues/2552)

# [2.72.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.71.0...v2.72.0) (2026-07-26)


### Features

* **passage:** la reactivation recupere les observations d une nuit rapatriee ([#2564](https://github.com/echonuit/vigiechiro-pr-companion/issues/2564)) ([3d2b13c](https://github.com/echonuit/vigiechiro-pr-companion/commit/3d2b13c6751876e199100808a165ff21a64372c7)), closes [#2555](https://github.com/echonuit/vigiechiro-pr-companion/issues/2555) [#2555](https://github.com/echonuit/vigiechiro-pr-companion/issues/2555) [#1828](https://github.com/echonuit/vigiechiro-pr-companion/issues/1828) [#1688](https://github.com/echonuit/vigiechiro-pr-companion/issues/1688) [#1309](https://github.com/echonuit/vigiechiro-pr-companion/issues/1309) [#814](https://github.com/echonuit/vigiechiro-pr-companion/issues/814) [#2483](https://github.com/echonuit/vigiechiro-pr-companion/issues/2483)

# [2.71.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.70.0...v2.71.0) (2026-07-26)


### Features

* **sites:** dériver de l'API le carré appartenant à un tiers ([#2525](https://github.com/echonuit/vigiechiro-pr-companion/issues/2525)) ([#2560](https://github.com/echonuit/vigiechiro-pr-companion/issues/2560)) ([b178618](https://github.com/echonuit/vigiechiro-pr-companion/commit/b1786184554403c2ab108ec702c7c559656791b9)), closes [#2483](https://github.com/echonuit/vigiechiro-pr-companion/issues/2483)

# [2.70.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.69.0...v2.70.0) (2026-07-26)


### Features

* **analyse:** export image de la courbe d'activité, redessiné et porteur de son contexte ([#2559](https://github.com/echonuit/vigiechiro-pr-companion/issues/2559)) ([432858f](https://github.com/echonuit/vigiechiro-pr-companion/commit/432858f66ca3579c8442e3da27a1575a5063a645)), closes [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352)

# [2.69.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.68.0...v2.69.0) (2026-07-26)


### Features

* **cli:** commande exporter-activite, parité CLI de la courbe d'activité ([#2553](https://github.com/echonuit/vigiechiro-pr-companion/issues/2553)) ([8c669ad](https://github.com/echonuit/vigiechiro-pr-companion/commit/8c669ad80eded450407655769d677f4ab486f838)), closes [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352)

# [2.68.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.67.0...v2.68.0) (2026-07-26)


### Features

* **saison:** exclure et distinguer les nuits opportunistes du solde ([#2525](https://github.com/echonuit/vigiechiro-pr-companion/issues/2525), PR 5/5) ([#2552](https://github.com/echonuit/vigiechiro-pr-companion/issues/2552)) ([7df2a84](https://github.com/echonuit/vigiechiro-pr-companion/commit/7df2a8427b44054c4caec118fba24fa13a661662)), closes [#2356](https://github.com/echonuit/vigiechiro-pr-companion/issues/2356)

# [2.67.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.66.0...v2.67.0) (2026-07-26)


### Features

* **depot:** les grosses archives se déposent en parties réessayables ([#2354](https://github.com/echonuit/vigiechiro-pr-companion/issues/2354)) ([#2548](https://github.com/echonuit/vigiechiro-pr-companion/issues/2548)) ([d7c5b95](https://github.com/echonuit/vigiechiro-pr-companion/commit/d7c5b953c4b14c723116d4227e9310a9b6468347)), closes [#2523](https://github.com/echonuit/vigiechiro-pr-companion/issues/2523)

# [2.66.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.65.0...v2.66.0) (2026-07-26)


### Features

* **analyse:** aplat de la fenêtre nocturne sous la courbe d'activité ([#2549](https://github.com/echonuit/vigiechiro-pr-companion/issues/2549)) ([a887457](https://github.com/echonuit/vigiechiro-pr-companion/commit/a887457473f548a0904ddd72fce8fd628697e34b)), closes [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352)

# [2.65.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.64.0...v2.65.0) (2026-07-26)


### Features

* **passage:** case « participation opportuniste » dans la modale « Modifier le passage » ([#2525](https://github.com/echonuit/vigiechiro-pr-companion/issues/2525), PR 3/5) ([#2545](https://github.com/echonuit/vigiechiro-pr-companion/issues/2545)) ([61cec50](https://github.com/echonuit/vigiechiro-pr-companion/commit/61cec50c61c9f0f786bf0a989e9bf03b72c777a1))

# [2.64.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.63.0...v2.64.0) (2026-07-26)


### Features

* **analyse:** étiquettes au pic et états vides nommés sur la courbe d'activité ([#2546](https://github.com/echonuit/vigiechiro-pr-companion/issues/2546)) ([0fded20](https://github.com/echonuit/vigiechiro-pr-companion/commit/0fded203c7f8da118e87896a31fa41d0f72de165)), closes [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352)

# [2.63.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.62.0...v2.63.0) (2026-07-26)


### Features

* **import:** déclarer la nature opportuniste d'une nuit à l'import ([#2525](https://github.com/echonuit/vigiechiro-pr-companion/issues/2525), PR 2/5) ([#2543](https://github.com/echonuit/vigiechiro-pr-companion/issues/2543)) ([d54d6a3](https://github.com/echonuit/vigiechiro-pr-companion/commit/d54d6a3e3eef91894b4c48145627507a230fe84d))

# [2.62.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.61.0...v2.62.0) (2026-07-26)


### Features

* **analyse:** survol des points de la courbe d'activité (heure · N contacts) ([#2542](https://github.com/echonuit/vigiechiro-pr-companion/issues/2542)) ([372a8d2](https://github.com/echonuit/vigiechiro-pr-companion/commit/372a8d268c0f87de55e16989282243ce77be21b6)), closes [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352)

# [2.61.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.60.0...v2.61.0) (2026-07-26)


### Features

* **analyse:** cascade géographique carré → point → nuit de la vue Activité ([#2539](https://github.com/echonuit/vigiechiro-pr-companion/issues/2539)) ([faffd32](https://github.com/echonuit/vigiechiro-pr-companion/commit/faffd32aee5e91a06a033b0080af30e6ca2c31db)), closes [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352)

# [2.60.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.59.0...v2.60.0) (2026-07-26)


### Features

* **passage:** socle des participations opportunistes + exemption R3/R4 ([#2525](https://github.com/echonuit/vigiechiro-pr-companion/issues/2525), PR 1/5) ([#2534](https://github.com/echonuit/vigiechiro-pr-companion/issues/2534)) ([df5f087](https://github.com/echonuit/vigiechiro-pr-companion/commit/df5f08713968ab678ffe6f6a3f5ac7d714630b4e))

# [2.59.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.58.0...v2.59.0) (2026-07-26)


### Features

* **depot:** mention discrète de reprise réseau sur la ligne d unité ([#2354](https://github.com/echonuit/vigiechiro-pr-companion/issues/2354)) ([#2535](https://github.com/echonuit/vigiechiro-pr-companion/issues/2535)) ([5ef7835](https://github.com/echonuit/vigiechiro-pr-companion/commit/5ef7835b110c8eae0f55566ad46e3a37a03b1ce5)), closes [#2350](https://github.com/echonuit/vigiechiro-pr-companion/issues/2350)

# [2.58.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.57.0...v2.58.0) (2026-07-26)


### Features

* **analyse:** barre de filtres de la vue Activité (carré, taxon parent, recherche) ([#2531](https://github.com/echonuit/vigiechiro-pr-companion/issues/2531)) ([8e61dcf](https://github.com/echonuit/vigiechiro-pr-companion/commit/8e61dcfef55f1c5d145ad32d15b0f3d979fbeb00)), closes [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352)

# [2.57.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.56.0...v2.57.0) (2026-07-26)


### Features

* **campagne:** rattachement à une campagne dans la modale « Modifier le passage » (lot 1, PR 3/4) ([#2529](https://github.com/echonuit/vigiechiro-pr-companion/issues/2529)) ([5def95f](https://github.com/echonuit/vigiechiro-pr-companion/commit/5def95feacf36af268fa76b559c18a1a1c5f7f88)), closes [#2355](https://github.com/echonuit/vigiechiro-pr-companion/issues/2355) [#2355](https://github.com/echonuit/vigiechiro-pr-companion/issues/2355)

# [2.56.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.55.0...v2.56.0) (2026-07-26)


### Features

* **depot:** le téléversement S3 réessaie une coupure momentanée ([#2354](https://github.com/echonuit/vigiechiro-pr-companion/issues/2354)) ([#2527](https://github.com/echonuit/vigiechiro-pr-companion/issues/2527)) ([aa10ede](https://github.com/echonuit/vigiechiro-pr-companion/commit/aa10ede45595b6960339acdb6c6e761fa4857804)), closes [#2350](https://github.com/echonuit/vigiechiro-pr-companion/issues/2350)

# [2.55.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.54.0...v2.55.0) (2026-07-26)


### Features

* **analyse:** entrée transverse de la vue Activité (carte d'accueil) ([#2526](https://github.com/echonuit/vigiechiro-pr-companion/issues/2526)) ([feba4ee](https://github.com/echonuit/vigiechiro-pr-companion/commit/feba4ee9bbee1a5aa70f9ba90c953e994f3719fa)), closes [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352)

# [2.54.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.53.0...v2.54.0) (2026-07-26)


### Features

* **api:** socle de la politique de réessai réseau ([#2354](https://github.com/echonuit/vigiechiro-pr-companion/issues/2354)) ([#2523](https://github.com/echonuit/vigiechiro-pr-companion/issues/2523)) ([0755159](https://github.com/echonuit/vigiechiro-pr-companion/commit/07551596db2e5646504fd986fe935052dfb10f05)), closes [#2350](https://github.com/echonuit/vigiechiro-pr-companion/issues/2350) [#2350](https://github.com/echonuit/vigiechiro-pr-companion/issues/2350)

# [2.53.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.52.0...v2.53.0) (2026-07-26)


### Features

* **campagne:** lien passage↔campagne (lot 1, PR 2/4) ([#2519](https://github.com/echonuit/vigiechiro-pr-companion/issues/2519)) ([a00533c](https://github.com/echonuit/vigiechiro-pr-companion/commit/a00533c540df432969f274a572c844fc48002a44)), closes [#2355](https://github.com/echonuit/vigiechiro-pr-companion/issues/2355)

# [2.52.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.51.0...v2.52.0) (2026-07-26)


### Features

* **analyse:** écran Activité de la nuit atteignable depuis le passage ([#2509](https://github.com/echonuit/vigiechiro-pr-companion/issues/2509)) ([2ad9d3c](https://github.com/echonuit/vigiechiro-pr-companion/commit/2ad9d3c6019f6922c2298cc121442cee93423e18)), closes [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2348](https://github.com/echonuit/vigiechiro-pr-companion/issues/2348) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352)

# [2.51.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.50.0...v2.51.0) (2026-07-26)


### Features

* **campagne:** socle campagne (lot 1, PR 1/4) ([#2517](https://github.com/echonuit/vigiechiro-pr-companion/issues/2517)) ([ac050d2](https://github.com/echonuit/vigiechiro-pr-companion/commit/ac050d2b22aa1502ec54576f13f0022a4728f089)), closes [#2355](https://github.com/echonuit/vigiechiro-pr-companion/issues/2355)

# [2.50.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.49.1...v2.50.0) (2026-07-26)


### Features

* **saison:** écran « Ma saison » (lot 2, PR 3/3) ([#2511](https://github.com/echonuit/vigiechiro-pr-companion/issues/2511)) ([ebc10f1](https://github.com/echonuit/vigiechiro-pr-companion/commit/ebc10f13de9299c048a238d87d2883e97831c95b)), closes [#2356](https://github.com/echonuit/vigiechiro-pr-companion/issues/2356) [#1376](https://github.com/echonuit/vigiechiro-pr-companion/issues/1376) [#2355](https://github.com/echonuit/vigiechiro-pr-companion/issues/2355)

## [2.49.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.49.0...v2.49.1) (2026-07-26)


### Bug Fixes

* **passage:** la modale de rattachement tient sur tout écran (corps défilant, pied épinglé, météo 2 colonnes) ([#2510](https://github.com/echonuit/vigiechiro-pr-companion/issues/2510)) ([fc5c290](https://github.com/echonuit/vigiechiro-pr-companion/commit/fc5c29069912506456f3fc4ceca96a708ea317bb)), closes [#2496](https://github.com/echonuit/vigiechiro-pr-companion/issues/2496) [#2496](https://github.com/echonuit/vigiechiro-pr-companion/issues/2496) [#2496](https://github.com/echonuit/vigiechiro-pr-companion/issues/2496)

# [2.49.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.48.0...v2.49.0) (2026-07-26)


### Features

* **cli:** commande solde-saison + module saison (lot 2, PR 2/3) ([#2506](https://github.com/echonuit/vigiechiro-pr-companion/issues/2506)) ([7fabaef](https://github.com/echonuit/vigiechiro-pr-companion/commit/7fabaef6c0d04439fd78f334013259ef30401b28)), closes [#1537](https://github.com/echonuit/vigiechiro-pr-companion/issues/1537)

# [2.48.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.47.0...v2.48.0) (2026-07-26)


### Features

* **analyse:** service et ViewModel de la courbe d'activité d'une nuit ([#2505](https://github.com/echonuit/vigiechiro-pr-companion/issues/2505)) ([23430af](https://github.com/echonuit/vigiechiro-pr-companion/commit/23430af7cb9630d2fac7115ded22463b456f0b77)), closes [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2348](https://github.com/echonuit/vigiechiro-pr-companion/issues/2348) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352)

# [2.47.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.46.0...v2.47.0) (2026-07-26)


### Features

* **saison:** modèle du solde de saison (lot 2, PR 1/3) ([#2502](https://github.com/echonuit/vigiechiro-pr-companion/issues/2502)) ([6963f0f](https://github.com/echonuit/vigiechiro-pr-companion/commit/6963f0f8292dc7391621a20b17b4cdfbeb705538)), closes [#2356](https://github.com/echonuit/vigiechiro-pr-companion/issues/2356)

# [2.46.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.45.3...v2.46.0) (2026-07-26)


### Features

* **analyse:** agréger l'activité d'une nuit par espèce et tranche horaire ([#2500](https://github.com/echonuit/vigiechiro-pr-companion/issues/2500)) ([ceb0bc0](https://github.com/echonuit/vigiechiro-pr-companion/commit/ceb0bc03b0cc2e84f43a8720640e74d1e9d8a188)), closes [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352) [#2348](https://github.com/echonuit/vigiechiro-pr-companion/issues/2348) [#2352](https://github.com/echonuit/vigiechiro-pr-companion/issues/2352)

## [2.45.3](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.45.2...v2.45.3) (2026-07-25)


### Bug Fixes

* **sites:** les modales à révélation suivent la croissance, et un garde-fou les détecte ([#2493](https://github.com/echonuit/vigiechiro-pr-companion/issues/2493)) ([#2497](https://github.com/echonuit/vigiechiro-pr-companion/issues/2497)) ([d349a2f](https://github.com/echonuit/vigiechiro-pr-companion/commit/d349a2fb3930e58f752ea1964a580b9edaafb43f)), closes [#2486](https://github.com/echonuit/vigiechiro-pr-companion/issues/2486) [#1534](https://github.com/echonuit/vigiechiro-pr-companion/issues/1534) [#2496](https://github.com/echonuit/vigiechiro-pr-companion/issues/2496) [#2467](https://github.com/echonuit/vigiechiro-pr-companion/issues/2467)

## [2.45.2](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.45.1...v2.45.2) (2026-07-25)


### Bug Fixes

* **connexion:** le bandeau reserve sa place au lieu de pousser les boutons dehors ([#2486](https://github.com/echonuit/vigiechiro-pr-companion/issues/2486)) ([b607317](https://github.com/echonuit/vigiechiro-pr-companion/commit/b607317ed5b2f73ec4498a69cb6dd0369b9f9afd)), closes [#1534](https://github.com/echonuit/vigiechiro-pr-companion/issues/1534)

## [2.45.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.45.0...v2.45.1) (2026-07-24)


### Bug Fixes

* **lot:** masquer « Libérer l'espace disque » quand il n'y a rien à supprimer ([#2028](https://github.com/echonuit/vigiechiro-pr-companion/issues/2028)) ([#2477](https://github.com/echonuit/vigiechiro-pr-companion/issues/2477)) ([7535584](https://github.com/echonuit/vigiechiro-pr-companion/commit/753558478d907fb2e3431afa83308959498f8be1)), closes [#1995](https://github.com/echonuit/vigiechiro-pr-companion/issues/1995)

# [2.45.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.44.1...v2.45.0) (2026-07-23)


### Features

* **audio:** sonder le dossier avant d'exporter la bibliothèque ([#2426](https://github.com/echonuit/vigiechiro-pr-companion/issues/2426)) ([#2470](https://github.com/echonuit/vigiechiro-pr-companion/issues/2470)) ([4ea03c8](https://github.com/echonuit/vigiechiro-pr-companion/commit/4ea03c8c4b78ff2f0a950ea0dc05ffad1f9baa55)), closes [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258)

## [2.44.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.44.0...v2.44.1) (2026-07-23)


### Bug Fixes

* **cli:** un refus metier sort en 2 (etat intact), pas en 1 (convention [#2294](https://github.com/echonuit/vigiechiro-pr-companion/issues/2294)) ([#2456](https://github.com/echonuit/vigiechiro-pr-companion/issues/2456)) ([82ea5f5](https://github.com/echonuit/vigiechiro-pr-companion/commit/82ea5f541aec31f0bb387b829dd95fb2dd313ee7))

# [2.44.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.43.0...v2.44.0) (2026-07-23)


### Features

* **importation:** importer un dossier de transformés locaux par référence ([#2450](https://github.com/echonuit/vigiechiro-pr-companion/issues/2450)) ([a95109e](https://github.com/echonuit/vigiechiro-pr-companion/commit/a95109e9bf77bba0ef3c5c7ed8befea50b1f8468)), closes [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258) [#2433](https://github.com/echonuit/vigiechiro-pr-companion/issues/2433) [#2255](https://github.com/echonuit/vigiechiro-pr-companion/issues/2255) [#2294](https://github.com/echonuit/vigiechiro-pr-companion/issues/2294) [#2433](https://github.com/echonuit/vigiechiro-pr-companion/issues/2433) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258) [#2433](https://github.com/echonuit/vigiechiro-pr-companion/issues/2433) [#2433](https://github.com/echonuit/vigiechiro-pr-companion/issues/2433) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258) [#2433](https://github.com/echonuit/vigiechiro-pr-companion/issues/2433) [#2433](https://github.com/echonuit/vigiechiro-pr-companion/issues/2433)

# [2.43.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.42.0...v2.43.0) (2026-07-23)


### Features

* **cli:** commande emplacements, parité de l'écran de choix (ADR 1038) ([#2410](https://github.com/echonuit/vigiechiro-pr-companion/issues/2410)) ([b42d8af](https://github.com/echonuit/vigiechiro-pr-companion/commit/b42d8af9343407d356ae2df59bb2dc1161be09f9)), closes [#1038](https://github.com/echonuit/vigiechiro-pr-companion/issues/1038) [#1038](https://github.com/echonuit/vigiechiro-pr-companion/issues/1038) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258)

# [2.42.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.41.0...v2.42.0) (2026-07-23)


### Features

* **emplacements:** écran de choix du dossier de travail et de la base (ADR 1038) ([#2401](https://github.com/echonuit/vigiechiro-pr-companion/issues/2401)) ([fcbaf98](https://github.com/echonuit/vigiechiro-pr-companion/commit/fcbaf9853cccd8db95fd8a8a39d5744ddd16bd3d)), closes [#1038](https://github.com/echonuit/vigiechiro-pr-companion/issues/1038) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258)

# [2.41.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.40.1...v2.41.0) (2026-07-23)


### Features

* **emplacements:** service métier pour lire et écrire où vivent base et workspace (ADR 1038) ([#2399](https://github.com/echonuit/vigiechiro-pr-companion/issues/2399)) ([8c6ba90](https://github.com/echonuit/vigiechiro-pr-companion/commit/8c6ba9036691d044dcec03c3788968eea8bd76a1)), closes [#1038](https://github.com/echonuit/vigiechiro-pr-companion/issues/1038) [#1038](https://github.com/echonuit/vigiechiro-pr-companion/issues/1038) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258)

## [2.40.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.40.0...v2.40.1) (2026-07-23)


### Bug Fixes

* **amorcage:** migrer avant de composer, pour lire les drapeaux à jour (ADR 1038) ([#2395](https://github.com/echonuit/vigiechiro-pr-companion/issues/2395)) ([53098d8](https://github.com/echonuit/vigiechiro-pr-companion/commit/53098d8f1161e12c6cc90bc15cb45f444d11bd3d)), closes [#1038](https://github.com/echonuit/vigiechiro-pr-companion/issues/1038) [#2187](https://github.com/echonuit/vigiechiro-pr-companion/issues/2187) [#1038](https://github.com/echonuit/vigiechiro-pr-companion/issues/1038) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258)

# [2.40.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.39.0...v2.40.0) (2026-07-23)


### Features

* **amorcage:** lire où vivent la base et le dossier de travail avant de démarrer (ADR 1038) ([#2393](https://github.com/echonuit/vigiechiro-pr-companion/issues/2393)) ([af058e0](https://github.com/echonuit/vigiechiro-pr-companion/commit/af058e084c003956ec7b74a0f1cffb89583abb4c)), closes [#1038](https://github.com/echonuit/vigiechiro-pr-companion/issues/1038) [#1038](https://github.com/echonuit/vigiechiro-pr-companion/issues/1038) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258) [#2389](https://github.com/echonuit/vigiechiro-pr-companion/issues/2389)

# [2.39.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.38.1...v2.39.0) (2026-07-22)


### Features

* **adr:** rendre les decisions verifiables, et le declarer dans l'ADR ([#2389](https://github.com/echonuit/vigiechiro-pr-companion/issues/2389)) ([2421bd6](https://github.com/echonuit/vigiechiro-pr-companion/commit/2421bd6636705fb1136b1419ddbddf60b9ca3ecf))

## [2.38.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.38.0...v2.38.1) (2026-07-22)


### Bug Fixes

* **flatpak:** retirer un declencheur qui ne s'est jamais declenche ([#2345](https://github.com/echonuit/vigiechiro-pr-companion/issues/2345)) ([e97b7fa](https://github.com/echonuit/vigiechiro-pr-companion/commit/e97b7fa10657177310e183afd91eca70e62ed548)), closes [#2344](https://github.com/echonuit/vigiechiro-pr-companion/issues/2344) [#2191](https://github.com/echonuit/vigiechiro-pr-companion/issues/2191) [#2247](https://github.com/echonuit/vigiechiro-pr-companion/issues/2247)

# [2.38.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.37.0...v2.38.0) (2026-07-22)


### Features

* **reactivation:** offrir le choix copier / laisser sur place à l'écran (ADR 0048) ([#2361](https://github.com/echonuit/vigiechiro-pr-companion/issues/2361)) ([385956b](https://github.com/echonuit/vigiechiro-pr-companion/commit/385956b36eda20a8789fd900ca64a215303f9da5)), closes [#2255](https://github.com/echonuit/vigiechiro-pr-companion/issues/2255) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258) [#2255](https://github.com/echonuit/vigiechiro-pr-companion/issues/2255) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258)

# [2.37.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.36.0...v2.37.0) (2026-07-22)


### Features

* **reactivation:** rebrancher par référence, sans copier l'audio (ADR 0048) ([#2347](https://github.com/echonuit/vigiechiro-pr-companion/issues/2347)) ([4d52a22](https://github.com/echonuit/vigiechiro-pr-companion/commit/4d52a22379f514c2502d6ca635baaf7612a9ec4d)), closes [#2255](https://github.com/echonuit/vigiechiro-pr-companion/issues/2255) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258) [#2255](https://github.com/echonuit/vigiechiro-pr-companion/issues/2255) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258)

# [2.36.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.35.1...v2.36.0) (2026-07-22)


### Features

* **audio:** ne pas offrir l'écoute d'un fichier substitué (ADR 0048) ([#2334](https://github.com/echonuit/vigiechiro-pr-companion/issues/2334)) ([b511b28](https://github.com/echonuit/vigiechiro-pr-companion/commit/b511b281fdf812703d4fcbcac08b82642818a9f5)), closes [#2254](https://github.com/echonuit/vigiechiro-pr-companion/issues/2254) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258) [#2254](https://github.com/echonuit/vigiechiro-pr-companion/issues/2254) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258)

# [2.35.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.34.4...v2.35.0) (2026-07-22)


### Features

* **audit:** signaler un fichier présent dont le contenu a changé (ADR 0048) ([#2317](https://github.com/echonuit/vigiechiro-pr-companion/issues/2317)) ([33ae516](https://github.com/echonuit/vigiechiro-pr-companion/commit/33ae516856f9d504b19672726d82ad6e75d2e2c1)), closes [#2254](https://github.com/echonuit/vigiechiro-pr-companion/issues/2254) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258) [#2254](https://github.com/echonuit/vigiechiro-pr-companion/issues/2254) [#2258](https://github.com/echonuit/vigiechiro-pr-companion/issues/2258)

## [2.34.4](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.34.3...v2.34.4) (2026-07-22)


### Bug Fixes

* **flatpak:** ne pas embarquer le repertoire de construction dans la PR ([#2315](https://github.com/echonuit/vigiechiro-pr-companion/issues/2315)) ([25031d6](https://github.com/echonuit/vigiechiro-pr-companion/commit/25031d6e67e8ab217ce91d7ff461cf5f9b1d1a8a)), closes [#2247](https://github.com/echonuit/vigiechiro-pr-companion/issues/2247)

## [2.34.3](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.34.2...v2.34.3) (2026-07-22)


### Bug Fixes

* **tests:** calibrer les butoirs TestFX sur la variation réelle du runner ([#2307](https://github.com/echonuit/vigiechiro-pr-companion/issues/2307)) ([a39d031](https://github.com/echonuit/vigiechiro-pr-companion/commit/a39d0311b079c7d6d810bf40bc915364e7f7a152)), closes [#2120](https://github.com/echonuit/vigiechiro-pr-companion/issues/2120)

## [2.34.2](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.34.1...v2.34.2) (2026-07-22)


### Bug Fixes

* **paquet:** le runtime livre ne contient pas java.net.http, l'application ne demarre pas ([#2300](https://github.com/echonuit/vigiechiro-pr-companion/issues/2300)) ([13d9359](https://github.com/echonuit/vigiechiro-pr-companion/commit/13d9359f972a556a07d9a03bd63c8277b43a7827)), closes [#2299](https://github.com/echonuit/vigiechiro-pr-companion/issues/2299)

## [2.34.1](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.34.0...v2.34.1) (2026-07-22)


### Bug Fixes

* **cli:** un refus se dit 2 sur stderr, y compris pour restaurer ([#2294](https://github.com/echonuit/vigiechiro-pr-companion/issues/2294)) ([#2296](https://github.com/echonuit/vigiechiro-pr-companion/issues/2296)) ([13cef18](https://github.com/echonuit/vigiechiro-pr-companion/commit/13cef183223c3d7f9bd0cf29cfced02a311abbcc)), closes [#2278](https://github.com/echonuit/vigiechiro-pr-companion/issues/2278)

# [2.34.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.33.0...v2.34.0) (2026-07-22)


### Features

* **cli:** importer --ecraser, derrière la même règle que la suppression ([#2278](https://github.com/echonuit/vigiechiro-pr-companion/issues/2278)) ([#2292](https://github.com/echonuit/vigiechiro-pr-companion/issues/2292)) ([a5de802](https://github.com/echonuit/vigiechiro-pr-companion/commit/a5de8020e69388040932c098f3c70b85b2c9eb8e)), closes [#279](https://github.com/echonuit/vigiechiro-pr-companion/issues/279) [#148](https://github.com/echonuit/vigiechiro-pr-companion/issues/148)

# [2.33.0](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.32.3...v2.33.0) (2026-07-22)


### Features

* **cli:** supprimer un passage en ligne de commande, derrière un drapeau ([#2278](https://github.com/echonuit/vigiechiro-pr-companion/issues/2278)) ([#2288](https://github.com/echonuit/vigiechiro-pr-companion/issues/2288)) ([3435c36](https://github.com/echonuit/vigiechiro-pr-companion/commit/3435c3639f444526e140561dbdfd001126684088))

## [2.32.3](https://github.com/echonuit/vigiechiro-pr-companion/compare/v2.32.2...v2.32.3) (2026-07-21)


### Bug Fixes

* **domaines:** basculer sur l'org echonuit et les domaines echonuit.fr ([#2274](https://github.com/echonuit/vigiechiro-pr-companion/issues/2274)) ([eae0a43](https://github.com/echonuit/vigiechiro-pr-companion/commit/eae0a437d599852b22e3182ec8f7b9681a597f9d)), closes [#2256](https://github.com/echonuit/vigiechiro-pr-companion/issues/2256)

## [2.32.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.32.1...v2.32.2) (2026-07-21)


### Bug Fixes

* **captures:** réenrouler le compte rendu d'un dialogue avant le snapshot ([#2243](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2243)) ([#2263](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2263)) ([14fee19](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/14fee1989d45267ad06b2c19bc6aab99f161fef9)), closes [#2223](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2223) [#2225](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2225)

## [2.32.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.32.0...v2.32.1) (2026-07-21)


### Bug Fixes

* **release:** construire l'app-image sans les options d'installeur ([#2259](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2259)) ([3299351](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/32993516b3d90f9b268ee58640c6a3f8e8584eef)), closes [#2256](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2256) [#2215](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2215) [#2215](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2215) [#2256](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2256)

# [2.32.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.31.0...v2.32.0) (2026-07-21)


### Features

* **captures:** montrer les deux avertissements migrés en LibelleRetour ([#2222](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2222)) ([#2250](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2250)) ([636e4cc](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/636e4cca55d16ca72541663cffda41fdb6fb0509)), closes [#2050](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2050) [#2097](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2097) [#2225](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2225) [#2050](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2050) [#2097](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2097)

# [2.31.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.30.5...v2.31.0) (2026-07-21)


### Features

* **import:** la seconde confirmation d’écrasement porte sa sévérité en compte rendu ([#2223](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2223)) ([#2244](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2244)) ([d1afa21](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d1afa2146d89c19401ff1d5bbc1089a880d7488e)), closes [#2239](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2239) [#2239](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2239) [#2060](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2060) [#2243](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2243) [#2225](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2225) [#2060](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2060)

## [2.30.5](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.30.4...v2.30.5) (2026-07-21)


### Bug Fixes

* **socle:** le confirmateur injectable délègue le compte rendu, et la suppression de passage l’utilise ([#2223](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2223)) ([#2239](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2239)) ([5c6abb2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5c6abb2c153ff4146a25aad6c2dbe68433dcc8ed)), closes [#2060](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2060) [#1013](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1013) [#2060](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2060) [#2060](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2060) [#2225](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2225) [#2060](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2060)

## [2.30.4](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.30.3...v2.30.4) (2026-07-21)


### Bug Fixes

* **lot:** retirer le glyphe de l’alerte espace disque, le badge le rend en couleur ([#2221](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2221)) ([#2234](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2234)) ([3c967e9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/3c967e9a748dc5a313ec5b294eb566f673bea9d2)), closes [#2225](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2225)

## [2.30.3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.30.2...v2.30.3) (2026-07-21)


### Bug Fixes

* **sites:** les pictogrammes de la feature sites se posent, ils ne s’écrivent plus ([#2221](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2221)) ([#2232](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2232)) ([c237229](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/c237229213345bad936a18cdb3b6a128a2d6aba9)), closes [#2036](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2036) [#2228](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2228) [#2225](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2225)

## [2.30.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.30.1...v2.30.2) (2026-07-21)


### Bug Fixes

* **qualification:** retirer le glyphe de sévérité de la barre de statut ([#2221](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2221)) ([#2228](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2228)) ([e5925b8](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e5925b865d9265f87d80830cbf3a7463083ca5bb)), closes [#1506](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1506) [#2225](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2225)

## [2.30.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.30.0...v2.30.1) (2026-07-21)


### Bug Fixes

* **sites:** rendre visible le chevron d’invite des cartes (collision de nom) ([#2217](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2217)) ([93fd54b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/93fd54b6cd9efda3a4e1b9d401928d571416fb7c)), closes [#1974](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1974)

# [2.30.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.29.0...v2.30.0) (2026-07-21)


### Features

* **sites:** trancher les deux cas-limites de sévérité dans le texte ([#2036](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2036) lot 4) ([#2216](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2216)) ([a41138d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/a41138d6e61c59c68ed611cbb91575bd15dc8271)), closes [#2056](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2056) [#2056](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2056) [#2188](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2188)

# [2.29.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.28.0...v2.29.0) (2026-07-21)


### Features

* **socle:** un Confirmateur peut confirmer un compte rendu, plus seulement une chaîne ([#2211](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2211)) ([477db0b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/477db0bb1aaeebc2729b2373ff7a78a6fa26c655)), closes [#1987](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1987) [#2050](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2050) [#2060](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2060) [#2188](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2188)

# [2.28.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.27.1...v2.28.0) (2026-07-20)


### Features

* **diagnostic:** l’alerte « hors nuit » porte sa sévérité, plus un triangle figé dans le FXML ([#2203](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2203)) ([ca50255](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ca502552f1b3848ba1dbf03deb5275c09f4b016e)), closes [#1990](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1990) [#2050](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2050) [#2050](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2050) [#2188](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2188)

## [2.27.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.27.0...v2.27.1) (2026-07-20)


### Bug Fixes

* **accessibilite:** rattraper vert et ambre littéraux, cliquet dédié ([#2201](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2201)) ([3768893](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/3768893d7f7e38305686514e368bd0c5f24e34f5)), closes [#2197](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2197) [#1e8449](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1e8449) [#1b8146](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1b8146) [#2115](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2115) [#2115](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2115) [#b9770e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/b9770e) [#a36100](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/a36100) [#b9770e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/b9770e) [#b7950b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/b7950b) [#2115](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2115) [#322](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/322) [#2102](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2102) [#322](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/322) [#b9770e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/b9770e) [#1974](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1974) [#2c3e50](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2c3e50) [#ffffff](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/ffffff) [#2c3e50](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2c3e50) [#322](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/322)

# [2.27.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.26.3...v2.27.0) (2026-07-20)


### Features

* **qualification:** l’avertissement « à jeter » porte sa sévérité, plus une classe CSS figée ([#2200](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2200)) ([7e7a082](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/7e7a082792bd143929f7145be253773205b8b16b)), closes [#2050](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2050) [#2054](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2054) [#2069](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2069) [#2072](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2072) [#2075](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2075) [#2050](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2050) [#2050](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2050) [#2188](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2188)

## [2.26.3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.26.2...v2.26.3) (2026-07-20)


### Bug Fixes

* **accessibilite:** rattraper les gris littéraux et dériver les couples ([#2197](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2197)) ([3bb4b99](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/3bb4b99741a29b2e472077e357dd5871bb352407)), closes [#2102](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2102) [#2102](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2102) [#6a737d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/6a737d) [#656e78](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/656e78) [#1537](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1537) [#2042](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2042) [#322](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/322) [#2102](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2102)

## [2.26.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.26.1...v2.26.2) (2026-07-20)


### Bug Fixes

* **ihm:** le dialogue « À propos » ne compte plus des lignes, il les nomme ([#2189](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2189)) ([28db0d3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/28db0d383c053ac795098588ebd163b4c6416453)), closes [#2104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2104) [#1468](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1468) [#2104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2104) [#2144](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2144)

## [2.26.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.26.0...v2.26.1) (2026-07-20)


### Bug Fixes

* **release:** l'entrée de menu Linux porte enfin une catégorie valide ([#2183](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2183)) ([02a4012](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/02a4012a35d148702d681ad5fd3610b6eca52eec)), closes [#2111](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2111) [#2104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2104)

# [2.26.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.25.0...v2.26.0) (2026-07-20)


### Features

* **flatpak:** manifeste Flathub, éprouvé sur une vraie carte SD ([#2178](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2178)) ([f66e833](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/f66e833ccd1bd4b3e7f1c80521e66445327ed76e)), closes [#2107](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2107) [#2111](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2111) [#2104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2104)

# [2.25.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.24.0...v2.25.0) (2026-07-20)


### Features

* **ihm:** annoncer la mise à jour dans un bandeau du chrome ([#2173](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2173)) ([7705e30](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/7705e302b47d21ab104ca75f7dd5e24ac80061e9)), closes [#2104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2104) [#2109](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2109) [#2104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2104)

# [2.24.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.23.0...v2.24.0) (2026-07-20)


### Features

* **parallele:** ouvrir le socle aux phases de pipeline (1/3) ([#2174](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2174)) ([4601d03](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/4601d03cd33dcef2c6e2f1baa31c320d902affeb)), closes [#2039](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2039) [#2039](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2039) [#12](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/12) [#155](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/155)

# [2.23.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.22.0...v2.23.0) (2026-07-20)


### Features

* **maj:** savoir qu'une version plus récente est publiée ([#2167](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2167)) ([b007bc2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/b007bc233c4d116b1c6730dd9997db75d9c6f055)), closes [#2109](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2109) [#2104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2104)

# [2.22.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.21.4...v2.22.0) (2026-07-20)


### Features

* **commun:** l'application sait quelle version elle est, et sait le dire ([#2154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2154)) ([1bf2374](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/1bf2374e9826342ca2c0545d05798da77f1e0cdb)), closes [#2108](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2108) [#2104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2104) [#2144](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2144) [#2108](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2108)

## [2.21.4](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.21.3...v2.21.4) (2026-07-20)


### Bug Fixes

* **qualification:** les feux du pré-check portent une icône, pas un caractère ([#2156](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2156)) ([5609585](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5609585ae7346ea4843be22677d71fec082b89dd)), closes [#2036](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2036) [#2113](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2113) [#801](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/801) [#801](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/801) [#2036](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2036)

## [2.21.4](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.21.3...v2.21.4) (2026-07-20)


### Bug Fixes

* **qualification:** les feux du pré-check portent une icône, pas un caractère ([#2156](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2156)) ([5609585](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5609585ae7346ea4843be22677d71fec082b89dd)), closes [#2036](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2036) [#2113](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2113) [#801](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/801) [#801](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/801) [#2036](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2036)

## [2.21.3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.21.2...v2.21.3) (2026-07-20)


### Bug Fixes

* **release:** une empreinte par artefact, calculée sur la sortie de build ([#2150](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2150)) ([0f07ebb](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0f07ebbea4e5717ce3b5ed75eade6b9c8aacd6de)), closes [#2107](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2107) [#2104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2104)

## [2.21.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.21.1...v2.21.2) (2026-07-20)


### Bug Fixes

* **captures:** donner sa hauteur à « Sons & validation », dont l AudioView débordait ([#2141](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2141)) ([25b41aa](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/25b41aac6dc5ca30dacadcf1d58d7459498484b7)), closes [#2127](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2127) [audio-view#56](https://github.com/audio-view/issues/56) [#2129](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2129)

## [2.21.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.21.0...v2.21.1) (2026-07-20)


### Bug Fixes

* **release:** déclarer desktop-file-validate, sans quoi l'AppImage ne se construit qu'en local ([#2149](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2149)) ([56f02c4](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/56f02c4ad89ca2e139ca5d33dd73102cf15754fc)), closes [#2107](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2107) [#2104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2104)

# [2.21.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.20.2...v2.21.0) (2026-07-20)


### Features

* **ihm:** l'application porte enfin une icône, celle de l'enregistreur passif ([#2146](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2146)) ([b7c082f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/b7c082ff2702ab1b54aac9213451c9840c422a8e)), closes [#2104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2104)
* **release:** une AppImage, un fichier unique qu'on rend exécutable et qu'on lance ([#2142](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2142)) ([5183d80](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5183d80ed39dec8d250d9fc7a362d44792d33e27)), closes [#2107](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2107) [#2104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2104)

## [2.20.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.20.1...v2.20.2) (2026-07-20)


### Bug Fixes

* **a11y:** le vert franchit le seuil, l'ambre se dédouble pour le franchir sans se perdre ([#2143](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2143)) ([257f0b5](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/257f0b5f61385537fdafa528cb984515e5fde39c)), closes [#1e8449](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1e8449) [#1b8146](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1b8146)

## [2.20.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.20.0...v2.20.1) (2026-07-20)


### Bug Fixes

* **a11y:** le gris du texte discret franchit le seuil AA, de cinq points ([#2139](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2139)) ([3c67bf2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/3c67bf2b6cbca4424b59dc67ce40ddaf16448e79)), closes [#2085](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2085)

# [2.20.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.19.0...v2.20.0) (2026-07-20)


### Features

* **release:** publier les empreintes SHA-256, faute de signature ([#2138](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2138)) ([d9c36a2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d9c36a26679d2c1fb1f97c30fc924419c2368559)), closes [#2107](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2107) [#2104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2104)

# [2.19.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.18.0...v2.19.0) (2026-07-20)


### Bug Fixes

* **captures:** refuser un libellé rendu avec une ellipse horizontale ([#2127](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2127)) ([ae43e61](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ae43e61bc857e2fd4575c09cd7dc14ca0230c95d)), closes [#2049](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2049) [#1641](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1641) [#1701](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1701) [#1873](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1873) [#1579](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1579) [#2012](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2012)


### Features

* **ihm:** « Colonnes… » prend son icône, comme le reste de son menu ([#2134](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2134)) ([8421dd4](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/8421dd45ad14b84d1ace476102d64976fe22d93b)), closes [#2065](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2065)

# [2.18.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.17.0...v2.18.0) (2026-07-20)


### Bug Fixes

* **ci:** la PR d'aperçus publie elle-même le check de titre qui la bloquait ([#2130](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2130)) ([fefc5fe](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/fefc5fee79f91d26637f87bb902d967c15755380)), closes [#2124](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2124) [#2106](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2106) [#2104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2104)
* **ihm:** les lignes d'inspection et la checklist du lot prennent leur icone ([#2113](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2113)) ([c398529](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/c3985298b12eb2a2cc2c4b74efc966123b7347b7)), closes [#1564](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1564) [#2099](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2099) [#2099](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2099)


### Features

* **release:** une archive portable, qui se décompresse au lieu de s'installer ([#2133](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2133)) ([b428904](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/b428904d76a1db8424ea732aacd494e7bde55360)), closes [#2107](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2107) [#2104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2104)


### Performance Improvements

* **ci:** le packaging ne dépend plus de la suite de tests, et rougit tout seul ([#2125](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2125)) ([e6f8648](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e6f8648de4b7b6678ff1e46e7c1d333c182129b8)), closes [#2106](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2106)

# [2.17.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.16.1...v2.17.0) (2026-07-20)


### Bug Fixes

* **1860:** convertir l'heure d'une nuit au lieu d'en jeter le décalage ([#1877](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1877)) ([9af9c5c](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9af9c5c1ce93ba4aea3aa74c478056ed9938ce69)), closes [#1861](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1861) [#1828](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1828)
* **1866:** la commande CLI ne synchronise pas, elle récupère ([#1899](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1899)) ([ee2667b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ee2667bc4fa442ff9e0a8304ee267d7ae8727920)), closes [#1855](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1855) [#1855](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1855)
* **1866:** la plateforme s'écrit « Vigie-Chiro » partout où on la lit ([#1896](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1896)) ([ec21082](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ec21082856a5f1206697b47aef925a58c0df3dad))
* **accueil:** le titre d'une carte s'enroule au lieu d'être coupé ([#2046](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2046)) ([#2051](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2051)) ([ab4d6e0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ab4d6e076f8a70a0dc86f6c8e5f25dd7a3c06763)), closes [#1933](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1933)
* **captures:** refuser une capture dont un libellé est tronqué ([#2092](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2092)) ([9d650dd](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9d650dd60d390261beaf0587107d29289ae4ab26)), closes [#2049](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2049)
* **hydratation:** rejouer l'arbitrage des noms, comme l'import le fait ([#1956](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1956)) ([83d51dd](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/83d51dd63bc6d69af9ea8e0155242214ea3d6e51)), closes [#1934](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1934) [#1932](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1932) [#1932](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1932)
* **ihm:** dire le motif du blocage là où il se lit, dans l'infobulle ([#1970](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1970)) ([#2010](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2010)) ([ed7d4d7](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ed7d4d78d38492465cb21db420fd3e2deacf7346)), closes [#790](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/790) [#789](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/789) [#1980](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1980)
* **ihm:** la barre d'actions d'un écran plie au lieu de tronquer ([#2012](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2012)) ([#2048](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2048)) ([16cf62a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/16cf62a9345ab59d154a786826ce425aa63f3a21)), closes [#789](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/789) [#789](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/789) [#1300](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1300) [#1302](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1302)
* **modales:** la fenêtre suit la croissance de son contenu ([#1931](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1931)) ([915af34](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/915af34ca5c386df142c9187ff42e9a597420988)), closes [#1534](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1534) [#1708](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1708)
* **modales:** ne pas figer le dimensionnement du Stage en voulant ne jamais rétrécir ([#1940](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1940)) ([4d2b263](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/4d2b263f8812f5eae7a64d14e05d3977e50fd62a)), closes [#1931](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1931)
* **rattachement:** « non connecté » cesse de s'annoncer comme un succès ([#2025](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2025)) ([e15d60f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e15d60fa559d559a062a2ed11d4d72335a4e0993))
* **reactivation:** import et réactivation doivent être le même pipeline de transformation ([#1932](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1932)) ([f0b837d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/f0b837df15186d4b878c0953dfd1f60132b78438))
* **reactivation:** ne pas redécouper ce qui est déjà là, et refuser un indice impossible ([#1975](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1975)) ([d421931](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d42193163ca235db74eb9570a5bd1c82ab836835)), closes [#1944](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1944) [#1962](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1962) [#1963](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1963)
* **reactivation:** nommer le travail qui se fait entre les deux phases ([#1951](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1951)) ([2d6dcd2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/2d6dcd25be864a30f08a528c8c322a4f614c427c)), closes [#1780](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1780) [#1935](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1935)
* **reactivation:** revendiquer les séquences par leur nom, pas par leur rattachement ([#1947](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1947)) ([b76ed69](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/b76ed69826d82f7478b82914c36fa13829f5fafd)), closes [#1937](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1937) [#1932](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1932) [#1932](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1932) [#1932](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1932)
* **release:** la publication reprend, et une dérive de convention rougit désormais ([#2117](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2117)) ([762c4ef](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/762c4ef72ef5c4eb843bd10a71b28413d98c31ba)), closes [#2105](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2105)
* **tests:** main rouge, un mock d'ImportObservations mentait sur son contrat ([#1927](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1927)) ([6c319db](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/6c319dbc0b635a98dafaa34bfdf51a559e495f8a)), closes [#1921](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1921) [#1867](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1867)


### Features

* **1861:** les métadonnées d'une nuit en ligne de commande, et le rattrapage d'une saison ([#1903](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1903)) ([430056c](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/430056cb90378bdce0d9e62251d4f0a0291e6bc4)), closes [#1814](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1814) [#1828](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1828) [#1844](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1844) [#1878](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1878) [#1708](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1708) [#1892](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1892)
* **1865:** documenter les deux modales de la publication ([#1911](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1911)) ([0c4b85c](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0c4b85ce3377f0c90764b08d76e6126dd80e1d0f)), closes [#1867](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1867) [#1468](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1468)
* **1865:** ouvrir le menu ☰ de Sons & validation, et le connecter ([#1907](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1907)) ([ac36c08](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ac36c0866a3a357453fbcb302ec3e91ba9fb866d))
* **1867:** dire que le rapatriement ramène aussi les mots du validateur ([#1901](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1901)) ([8e90c94](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/8e90c9425f6bd870767920f1bdabe99489f01211)), closes [#1838](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1838)
* **1878:** les heures qu'une nuit prouve font autorité sur celles qu'elle déclare ([#1882](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1882)) ([06cb1cc](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/06cb1cc5dbb6ca15ac8c4c108b22886ab9673238)), closes [#1860](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1860)
* **1885:** dire quand les heures d'une nuit ont été réalignées ([#1891](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1891)) ([f952f2d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/f952f2db30dfc13020585145a6250e245908102a)), closes [#1878](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1878) [#1839](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1839) [#1839](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1839)
* **1892:** corriger à la main les heures d'une nuit que rien ne prouve ([#1898](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1898)) ([216c7da](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/216c7daf4ea777e4762530ac6f5bf6ffffa589a8)), closes [#1860](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1860) [#1878](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1878)
* **1904:** la réactivation dit aussi ce que son ancrage a rapatrié ([#1916](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1916)) ([d399183](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d3991831ffa054c5c98c6b2f3caa66b5a596e51e)), closes [#1867](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1867)
* **captures:** photographier un menu ouvert devient un geste du socle ([#2065](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2065)) ([#2081](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2081)) ([93d5f89](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/93d5f8900e687adda57c9c6daadd20147050f0ac)), closes [#1564](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1564)
* **cli:** `importer` suit le réglage de conservation, et l'ADR du chantier (lot 2 de [#2061](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2061)) ([#2084](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2084)) ([f650bcc](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/f650bcc42b706d602623b3300cd2fc8d1734572d)), closes [#2064](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2064) [#2085](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2085) [#2085](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2085)
* **commun:** le compte rendu devient une structure, avec son composant ([#2008](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2008)) ([6e17d77](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/6e17d7787085ef5acb5b941a5280be11d1588288)), closes [#1987](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1987)
* **diagnostic,qualification:** passer le retour d'opération au bandeau (Lot 1, [#1887](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1887)) ([#1912](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1912)) ([aec9569](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/aec9569774a91043e5581e2464500f4087bde144)), closes [#1870](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1870)
* **ihm:** la poignée de colonnes et les repères de lieu quittent les caractères (lot 3 de [#1564](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1564)) ([#2086](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2086)) ([3131029](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/3131029c0f01687db488c0cbe4e74f4ea1a3fd84)), closes [#1933](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1933) [#2050](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2050)
* **ihm:** la sévérité se rend une fois, et les titres prennent leur icône (lot 5 de [#1933](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1933)) ([#2026](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2026)) ([7da89ea](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/7da89eac292e56150932189f544e62b44373da9b))
* **ihm:** les contrôles de carte dont le glyphe est le libellé prennent leur icône (lot 2 de [#1564](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1564)) ([#2073](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2073)) ([bae78e0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/bae78e09d69649f2a759e74ceba2a09315a3a27c)), closes [#1933](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1933)
* **ihm:** les entrées du menu ☰ portent leur icône, plus leur pictogramme (lot 4 de [#1933](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1933)) ([#2024](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2024)) ([176d820](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/176d820d797face24447a397c6f93d814377d253))
* **ihm:** les glyphes de commande deviennent des icônes (lot 1 de [#1933](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1933)) ([#1989](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1989)) ([43cb2ff](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/43cb2ff2c3f977244bbd663356f3871d5a3317a9)), closes [#700](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/700)
* **ihm:** les libellés « pictogramme + mots » bâtis en Java prennent leur icône (lot 1 de [#1564](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1564)) ([#2066](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2066)) ([8a9ab51](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/8a9ab51697a31302c29ba9cb47f5bf5f5ad2d542)), closes [#1933](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1933)
* **ihm:** les pictogrammes de section et d'en-tête deviennent des icônes (lot 3 de [#1933](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1933)) ([#2016](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2016)) ([3df3dbd](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/3df3dbd758f5b036991b507283fd267caeec7941)), closes [#794](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/794)
* **ihm:** les pictogrammes des actions deviennent des icônes (lot 2 de [#1933](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1933)) ([#2011](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2011)) ([4de51b5](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/4de51b5f8607bbde819b2bf26dd4dc0ae1fd2f5e))
* **import:** ce que l'inspection releve devient un compte rendu ([#2069](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2069)) ([e79d81b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e79d81beb238a2040817bcfee9987a338f776584)), closes [#2050](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2050)
* **import:** la copie des originaux devient une option de ré-analyse (lots 0 et 1 de [#2061](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2061)) ([#2074](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2074)) ([fa50cda](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/fa50cda38d06148b5581064ee3af32c1639ba26c)), closes [#1303](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1303) [#2062](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2062) [#2063](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2063)
* **import:** le blocage multi-nuits nomme les numeros deja pris ([#2054](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2054)) ([94c3b4d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/94c3b4ddd0f89533723e01e83e1a6a5c246e2447)), closes [#2050](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2050)
* **import:** le rapport d'import quitte la barre de statut ([#2044](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2044)) ([afe94cf](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/afe94cf25014dad81638416322345d843b8316f4)), closes [#2004](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2004) [#155](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/155)
* **import:** les avertissements bornes portent leur severite, plus un glyphe ([#2072](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2072)) ([32ee0d3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/32ee0d34cb0d220ece336e169ef353ed5305cf5e)), closes [#108](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/108) [#111](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/111) [#2050](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2050)
* **import:** refuser un import qui ne tiendra pas, avant d'écrire ([#2094](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2094)) ([f8a5253](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/f8a5253aec7018e599f5b2c6beb83947c861a66d)), closes [#769](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/769) [#2061](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2061) [#2041](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2041)
* **lot:** passer le retour d'opération au bandeau (Lot 4, [#1890](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1890)) ([#1942](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1942)) ([b93bebf](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/b93bebf6f264ef2f9bd6b5877541e764404051a6))
* **lot:** pipeliner le dépôt — générer, téléverser, libérer au fil de l'eau (EPIC [#1991](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1991)) ([fc59586](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/fc595869e919adbf5223004c1be5960ec5fc03ea)), closes [#1993](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1993) [#1994](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1994) [#1995](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1995) [#1996](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1996) [#1997](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1997) [#1998](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1998) [#1999](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1999) [#769](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/769) [#1244](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1244) [#2028](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2028) [#2029](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2029) [#2039](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2039) [#2043](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2043) [#2049](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2049) [#1993](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1993) [#1994](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1994) [#1995](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1995) [#1996](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1996) [#1997](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1997) [#1998](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1998) [#1999](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1999)
* **lot:** sortir les « en cours » du retour, et poser la règle du modal (Lot 0, [#1886](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1886)) ([#1897](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1897)) ([b9b0370](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/b9b03700fc6acf99948763fbe05d27b300efbd0d)), closes [#1543](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1543) [#1881](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1881)
* **modales:** chaque phase devient un bloc, et les modales retrouvent leurs marges ([#1935](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1935)) ([b55d351](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/b55d351b0cd8f1ca98f7b5bebaa5e2555932a93f)), closes [#789](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/789)
* **modales:** passer le retour d'opération au bandeau, 3 modales sur 5 ([#1917](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1917)) ([#1953](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1953)) ([c679585](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/c6795855715df092ff928e16585a7d7c9a07c48b))
* **multisite,sites:** passer le retour d'opération au bandeau (Lot 2, [#1888](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1888)) ([#1918](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1918)) ([5dc4918](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5dc4918a2a2be610554143f453e9ad6867a3215a))
* **passage:** passer le retour d'opération au bandeau (Lot 3, [#1889](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1889)) ([#1926](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1926)) ([d54527d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d54527d74051c5e181528e95e6a2f19516fcb647))
* **passage:** passer le retour de « Modifier le passage » au bandeau ([#1917](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1917)) ([#1960](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1960)) ([fd39ce4](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/fd39ce4aa1fa8a191caffed99e266ae049c67b78))
* **publication:** le bilan d'une publication devient un compte rendu ([#2033](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2033)) ([1e65ecb](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/1e65ecbfcc1afa1edacd9a3ee4aeb01e7769de57)), closes [#2004](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2004)
* **reactivation:** le compte rendu passe à la structure ([#2013](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2013)) ([f5bdaf0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/f5bdaf03a566f9939054c8b39cf7d336fe553214)), closes [#2002](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2002) [#1987](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1987) [#14532d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/14532d) [#7f1d1d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/7f1d1d)
* **reactivation:** nommer ce qui manque, et dire de quel manque il s'agit ([#1948](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1948)) ([7fa5ae2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/7fa5ae25f919ec8be9ed5033539cd8f3e05da4e4))
* **reconstruction:** aligner les deux barres de progression sur le patron en blocs ([#1946](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1946)) ([dcedc29](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/dcedc2981dfb2d5051555225e6abe44c662a86e2)), closes [#1708](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1708)
* **reglages:** l'aide d'un réglage se lit, elle ne se survole plus ([#2101](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2101)) ([45ae828](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/45ae828e5cd91289d789880d571ec859da566573)), closes [#2085](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2085)
* **socle:** la severite gagne le niveau qui lui manquait ([#2052](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2052)) ([3d1911d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/3d1911d9dcb92e0eca67f8de374a45ca84a4e232)), closes [#2050](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2050) [#2045](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2045)
* **socle:** un retour ne peut plus ouvrir par un glyphe de severite ([#2075](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2075)) ([2e829eb](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/2e829eb83d155df8c4d090b401d32f300a3763ec)), closes [#2052](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2052) [#2050](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/2050)


### Performance Improvements

* **demarrage:** le rétro-remplissage de l'horodatage tient en une transaction ([#1978](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1978)) ([640740a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/640740ab4f6e3d8c97d5918b8c0a838798ffa85e)), closes [#1966](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1966)
* **reactivation:** adopter les originaux en une transaction, et nommer ce geste ([#1959](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1959)) ([b62c1ad](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/b62c1ad6c075f6ba58dafc720b772966fa92f835)), closes [#1951](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1951)

## [2.16.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.16.0...v2.16.1) (2026-07-18)


### Performance Improvements

* **validation:** l'import explicite reprend le CSV, l'ancrage suit la publication ([#1838](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1838)) ([#1857](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1857)) ([b668f82](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/b668f827029f1521eff6f78c7ef282faac5f8287)), closes [#1417](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1417) [#1565](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1565) [#1264](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1264)

# [2.16.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.15.0...v2.16.0) (2026-07-18)


### Features

* **analyse:** dire pourquoi le double-clic n'ouvre pas de fiche ([#1852](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1852)) ([81680f3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/81680f34ea1a3830056f96b38a2bd538d0f2444a)), closes [#1837](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1837) [#1834](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1834)

# [2.15.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.14.0...v2.15.0) (2026-07-18)


### Features

* **cli:** la publication CLI acquiert aussi l'ancrage, et le journal enregistre la décision ([#1838](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1838)) ([#1850](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1850)) ([4dbab3e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/4dbab3e64d6461b04478acdeec44d8ec2d3f18f7)), closes [#1417](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1417)

# [2.14.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.13.0...v2.14.0) (2026-07-18)


### Features

* **audio:** câbler l'ancrage à la publication et rouvrir le geste aux nuits CSV ([#1838](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1838)) ([#1848](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1848)) ([815bd78](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/815bd7849d78a6e383a67c34f78d7ca38c46a098)), closes [#1596](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1596)

# [2.13.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.12.2...v2.13.0) (2026-07-18)


### Features

* **validation:** la publication acquiert elle-même l'ancrage qui lui manque ([#1838](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1838)) ([#1842](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1842)) ([8ffeaa4](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/8ffeaa4630a5e512dcddb0fdae0fc9c3061b86da)), closes [#1565](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1565) [#1571](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1571)

## [2.12.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.12.1...v2.12.2) (2026-07-18)


### Bug Fixes

* **audio:** dire pourquoi le double-clic n'ouvre pas de fiche ([#1836](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1836)) ([d066b5a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d066b5a32b03ed48fdc982494a947beb2f39eac0)), closes [#789](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/789)

## [2.12.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.12.0...v2.12.1) (2026-07-17)


### Bug Fixes

* **cli:** un refus de validation n'est plus journalisé comme un incident SEVERE ([#1744](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1744)) ([d98c185](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d98c1859201659997cd8cac47a14373067d9b055))

# [2.12.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.11.1...v2.12.0) (2026-07-17)


### Bug Fixes

* **audit:** reset déterministe, le rapprocheur des passages hors de sa synchro ([#1743](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1743)) ([18fee5d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/18fee5de0f29dd862deb8152a0dc259288087f97)), closes [#1707](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1707) [#1707](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1707)


### Features

* **passage:** inscrire l'empreinte sha256 des originaux hydratés ([#1726](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1726)) ([#1740](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1740)) ([fb143bc](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/fb143bc4f492ee16afe7b1dbcf72d3beeeedc94e)), closes [#1651](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1651) [#1299](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1299)

## [2.11.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.11.0...v2.11.1) (2026-07-17)


### Performance Improvements

* **passage:** n'hydrater que les bruts de la nuit du passage ([#1724](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1724)) ([#1737](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1737)) ([0906503](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0906503be5a20107cf628eb1382ed284bdf60276))

# [2.11.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.10.0...v2.11.0) (2026-07-12)


### Features

* **audit:** audit en ligne des points d écoute (issue K, EPIC [#1154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1154)) ([#1217](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1217)) ([0a910b1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0a910b19a16fbf3a56b6d3fe9e99ca7f710d9607)), closes [#1178](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1178) [#1132](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1132) [#1178](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1178) [#1178](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1178)

# [2.10.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.9.1...v2.10.0) (2026-07-12)


### Features

* **audit:** audit en ligne (confrontation au serveur, issue D, EPIC [#1154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1154)) ([#1196](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1196)) ([5b57418](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5b57418a68a524f13264fa1e0b5da10a1ad9c566)), closes [#1132](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1132) [#1138](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1138)

## [2.9.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.9.0...v2.9.1) (2026-07-12)


### Bug Fixes

* **api:** paginer mesSites/mesParticipations, toutes pages ([#1150](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1150)) ([#1186](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1186)) ([3b40948](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/3b409481f42ccd053e087043ec73e49608085002)), closes [#1050](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1050)

# [2.9.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.8.0...v2.9.0) (2026-07-12)


### Features

* **audit:** écran « Audit de cohérence » sur l'accueil (issue J, EPIC [#1154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1154)) ([#1182](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1182)) ([0f234d8](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0f234d897277034d7bc35f1002a510b459799aad)), closes [#1152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1152)

# [2.8.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.7.0...v2.8.0) (2026-07-12)


### Features

* **persistence:** sauvegarde complète base + dossiers de session (issue I, EPIC [#1154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1154)) ([#1175](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1175)) ([304f231](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/304f231f74fc10274bdc419f1b85ad5574743208)), closes [#1142](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1142)

# [2.7.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.6.0...v2.7.0) (2026-07-12)


### Features

* **passage:** interdire le renommage d un passage déposé (issue B, EPIC [#1154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1154)) ([#1168](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1168)) ([0aa6301](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0aa6301449feca1196678e52f63765907685fd9f)), closes [#1134](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1134) [#789](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/789)

# [2.6.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.5.0...v2.6.0) (2026-07-12)


### Features

* **audit:** audit de cohérence disque / base (issue A, EPIC [#1154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1154)) ([#1164](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1164)) ([1e6dd1a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/1e6dd1a8b92e8ec56e44431ac053a09f2cc40e12)), closes [#1133](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1133)

# [2.5.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.4.1...v2.5.0) (2026-07-12)


### Features

* **securite:** restreindre le fichier de connexion au propriétaire (600) ([#1158](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1158)) ([5ffa8b3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5ffa8b3bc04649101d1666c26fd61812b3faf15b)), closes [#1140](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1140) [#1141](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1141) [#1140](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1140) [#1143](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1143)

## [2.4.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.4.0...v2.4.1) (2026-07-12)


### Bug Fixes

* **securite:** ignorer connexion.json / vigiechiro.db + corriger la doc ([#1153](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1153)) ([4dceb55](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/4dceb55e44ed51275679aa61e20737845c87efa3)), closes [#1140](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1140) [#716](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/716) [#1140](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1140) [#1141](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1141)

# [2.4.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.3.0...v2.4.0) (2026-07-12)


### Features

* **cli:** option --archives, dépôt des ZIP au lieu des WAV (expérimental, [#984](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/984)) ([#1115](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1115)) ([198c63c](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/198c63cce17a3e309efa63748a2472e677619591)), closes [#1035](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1035)

# [2.3.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.2.0...v2.3.0) (2026-07-12)


### Features

* **lot:** annuler un dépôt VigieChiro en cours ([#1044](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1044)) ([#1105](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1105)) ([c49f3b5](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/c49f3b5a0719b090c3314479eefba1f51835f45a)), closes [#982](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/982) [#906](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/906)

# [2.2.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.1.0...v2.2.0) (2026-07-12)


### Features

* **design:** badge de statut unifié sur la colonne « Statut » audio ([#686](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/686)) ([#1102](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1102)) ([a3d0e4a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/a3d0e4aa633a72dfc7ac1e88b35c42e12a76772d)), closes [#691](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/691)

# [2.1.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v2.0.0...v2.1.0) (2026-07-11)


### Features

* **cli:** deposer-vigiechiro, dépôt reprenable en ligne de commande ([#1095](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1095)) ([5e7607d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5e7607dad0e3f5858564896785c91d4a67309d9a)), closes [#1043](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1043) [#982](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/982)

# [2.0.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.190.0...v2.0.0) (2026-07-11)


* refactor([#1052](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1052))! : « durée enregistrée » remplace « durée audible » ([#1089](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1089)) ([a0d66fc](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/a0d66fc69feb7ceaf48f7457c20aed6f99c76998)), closes [#1051](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1051) [#1051](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1051)


### BREAKING CHANGES

* la sortie JSON de la commande CLI statut-passage renomme la clé
dureeAudibleSecondes en dureeEnregistreeSecondes. Sa sémantique change de toute façon

# [1.190.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.189.0...v1.190.0) (2026-07-11)


### Features

* **statut:** identité en gauche de Sons & validation + conformité ([#1025](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1025)) ([#1084](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1084)) ([ec73596](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ec735963b7da09f76b27f10b5048d5663181f65a)), closes [#1016](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1016)

# [1.189.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.188.0...v1.189.0) (2026-07-11)


### Features

* **statut:** barre de statut pour l'assistant d'import ([#1024](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1024)) ([#1077](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1077)) ([42882e7](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/42882e76e88ff80ac2cf05117e41e299977488f1)), closes [#793](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/793)

# [1.188.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.187.0...v1.188.0) (2026-07-11)


### Features

* **statut:** barre de statut pour Multisite & Analyse ([#1023](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1023)) ([#1072](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1072)) ([8519cbe](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/8519cbee282227913d402afbde5dc48a9bc048d1))

# [1.187.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.186.0...v1.187.0) (2026-07-11)


### Features

* **statut:** barre de statut 3 zones pour Passage & Diagnostic ([#1022](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1022)) ([#1068](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1068)) ([38a7f1a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/38a7f1a42f84e00c59e73bf08d5118196128fb2a))

# [1.186.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.185.0...v1.186.0) (2026-07-11)


### Features

* **statut:** barre de statut 3 zones pour M-Qualification ([#1021](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1021)) ([#1066](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1066)) ([69cbf1e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/69cbf1e436b72fe0aa9f07aa5e4eab9c36b42e6c)), closes [#1031](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1031) [#686](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/686)

# [1.185.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.184.0...v1.185.0) (2026-07-11)


### Features

* **reglages:** onglet Audio — daltonien + export inclure-mode persistés ([#1006](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1006)) ([#1036](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1036)) ([46f97c1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/46f97c169a2436af5ce015a4ca14a3a099c75b17))

# [1.184.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.183.0...v1.184.0) (2026-07-11)


### Features

* **lot:** barre de statut 3 zones pour M-Lot (3f) ([#1031](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1031)) ([3ff63b7](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/3ff63b785ae77d8fce46fa93c5cdfd83d78a74eb)), closes [#823](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/823) [#495](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/495) [#982](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/982) [#983](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/983) [#769](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/769) [#805](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/805) [#941](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/941)

# [1.183.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.182.0...v1.183.0) (2026-07-11)


### Features

* **reglages:** onglet « Audio » — préférences de lecture persistées ([#1006](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1006)) ([#1018](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1018)) ([a5b8305](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/a5b83057a02b9f299e74476f0843c38e90433466))

# [1.182.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.181.0...v1.182.0) (2026-07-11)


### Features

* **statut:** socle barre de statut 3 zones (helpers gauche + droite) ([#1020](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1020)) ([#1030](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1030)) ([e5f50c1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e5f50c134a2d9279b00c56ecdfa07496a35d389f)), closes [#1016](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1016) [#823](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/823) [#1021](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1021) [#1024](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1024)

# [1.181.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.180.0...v1.181.0) (2026-07-11)


### Features

* **lot:** table de dépôt réhydratée + « Retenter les échecs » (3d) ([#1027](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1027)) ([9b83b54](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9b83b5499b42ef5bab1935dbb17232b2b860999a)), closes [#983](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/983) [#946](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/946) [#820](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/820) [#947](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/947) [#981](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/981) [#982](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/982) [#980](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/980)

# [1.180.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.179.0...v1.180.0) (2026-07-11)


### Features

* **lot:** moteur de dépôt reprenable par unité + upload en flux (3c) ([#1011](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1011)) ([0729c1d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0729c1df7970f1df6b09df3dea039a9923595928)), closes [#982](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/982) [#981](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/981) [#980](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/980) [SuiviArchives/#947](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/947) [#983](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/983)

# [1.179.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.178.2...v1.179.0) (2026-07-11)


### Features

* **reglages:** mise en page de l'écran Réglages (CSS) ([#1010](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1010)) ([284c391](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/284c391cd187583f54094ebe730b18d8b6eca0b8))

## [1.178.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.178.1...v1.178.2) (2026-07-11)


### Bug Fixes

* **feedback:** retours enrichis multi-nuits, feux, glisser-déposer ([#801](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/801)) ([#1009](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1009)) ([baddc1f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/baddc1fd20c168f8870d3e6745fb08572a8d7859))

## [1.178.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.178.0...v1.178.1) (2026-07-11)


### Bug Fixes

* **a11y:** tooltips manquants et en-tête d'alerte trompeur ([#801](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/801)) ([#999](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/999)) ([b491bc7](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/b491bc735ffae23e80772d52ff0d8b3cdc7ad212))

# [1.178.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.177.1...v1.178.0) (2026-07-11)


### Features

* **lot:** table depot_unite, suivi persisté du dépôt par unité (3b) ([#998](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/998)) ([7ed9390](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/7ed93903b8c2954412f393cb8ac41568ffdcaded)), closes [#981](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/981) [#982](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/982) [#983](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/983) [#980](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/980) [#982](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/982)

## [1.177.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.177.0...v1.177.1) (2026-07-11)


### Bug Fixes

* **ux:** afficher les feedbacks avalés (sélection d'écoute, recherche vide) ([#795](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/795)) ([#992](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/992)) ([d415e90](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d415e90b7351fffbd4087409488e13023c510a05))

# [1.177.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.176.3...v1.177.0) (2026-07-11)


### Features

* **passage:** statut « Dépôt en cours » dans le workflow (3a) ([#989](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/989)) ([7792a9c](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/7792a9c8b681ccca3990fd9cd53d251a5f3395b4)), closes [#980](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/980) [#982](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/982) [#982](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/982) [#981](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/981) [#980](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/980)

## [1.176.3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.176.2...v1.176.3) (2026-07-11)


### Bug Fixes

* **ux:** surfacer les erreurs de chargement avalées ([#795](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/795)) ([#986](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/986)) ([fe83efd](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/fe83efd632937c648bc9a212c86681771a544b85))

## [1.176.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.176.1...v1.176.2) (2026-07-11)


### Bug Fixes

* **ux:** confirmer les actions destructives sans garde-fou ([#798](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/798)) ([#966](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/966)) ([3d98476](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/3d9847670a2a0780915564001b55fa7d068fe058))

## [1.176.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.176.0...v1.176.1) (2026-07-11)


### Performance Improvements

* **import:** copie protégée parallèle (1e-C) ([#975](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/975)) ([ccbaa86](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ccbaa8631bdb4684668212f84e722084f2f5e155)), closes [#948](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/948) [#12](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/12) [#231](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/231) [#33](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/33) [#146](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/146) [#155](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/155) [#947](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/947)

# [1.176.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.175.0...v1.176.0) (2026-07-11)


### Features

* **menu:** menu ☰ extensible par DI (ActionMenu + Multibinder) (P2.1) ([#972](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/972)) ([f578b22](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/f578b228def19e03fda20cf329c4be2b2e6e2baf)), closes [#930](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/930)

# [1.175.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.174.0...v1.175.0) (2026-07-11)


### Features

* **import:** table de suivi par fichier dans M-Import (1e-B) ([#968](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/968)) ([d7e1f8f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d7e1f8ff091ea5631a14883c7e108942ead9d545)), closes [#947](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/947) [#33](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/33) [#820](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/820) [#231](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/231) [#155](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/155) [#12](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/12) [#947](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/947) [#946](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/946) [#155](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/155) [#12](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/12) [#820](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/820)

# [1.174.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.173.0...v1.174.0) (2026-07-11)


### Features

* **reglages:** onglets « Général » et « Import » + synchro ☰ (P1.3) ([#965](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/965)) ([002aea3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/002aea319eda9e4e0e4508ceb13bc31523d3f75d)), closes [#928](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/928)

# [1.173.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.172.3...v1.173.0) (2026-07-11)


### Features

* **reglages:** écran « Réglages » auto-rempli + entrée ☰ (P1.2) ([#958](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/958)) ([0850a1b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0850a1b92268dcfab0fd0ebee34c19548e8ea7e0))

## [1.172.3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.172.2...v1.172.3) (2026-07-11)


### Bug Fixes

* **a11y:** guider les ComboBox sans étiquette ni désactivation ([#800](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/800)) ([#960](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/960)) ([0817d77](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0817d77e35152e676430488c186c03bdf16ff947))

## [1.172.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.172.1...v1.172.2) (2026-07-11)


### Bug Fixes

* **a11y:** libellé accessible sur les icônes seules restantes ([#794](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/794)) ([#956](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/956)) ([bb52435](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/bb5243537260231357884ee94891ac07198072f7))

## [1.172.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.172.0...v1.172.1) (2026-07-11)


### Bug Fixes

* **affordance:** signaler les cibles interactives (danger import, carte, colonnes) ([#952](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/952)) ([b1b2b84](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/b1b2b8470ab529bf04f79115bee32800e51b2d45)), closes [#786](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/786) [#801](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/801) [#801](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/801)

# [1.172.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.171.0...v1.172.0) (2026-07-11)


### Features

* **reglages:** contrats d'extension + service typé + couche réactive (P1.1) ([#944](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/944)) ([e6c643a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e6c643a7c8c512b6bee4208e9d0d04b1569c6c4d)), closes [#923](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/923)

# [1.171.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.170.1...v1.171.0) (2026-07-11)


### Features

* **passage:** « Synchroniser depuis VigieChiro » dans la modale (tirer) — Phase 2b ([#937](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/937)) ([e50ae8e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e50ae8ef7ff0dd438de48e519e22c0ad0aeba2e3))

## [1.170.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.170.0...v1.170.1) (2026-07-11)


### Bug Fixes

* **design:** garder une ligne selectionnee lisible au survol ([#936](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/936)) ([fec6f62](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/fec6f6244e01751036a7af42b384df85875ee549)), closes [#792](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/792)

# [1.170.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.169.0...v1.170.0) (2026-07-11)


### Features

* **passage:** pousser meteo/micro vers la participation a la validation (bug B) — Phase 2a ([#934](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/934)) ([433f23e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/433f23ec580c2f536a45e7b0e77200854cf37e54))

# [1.169.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.168.0...v1.169.0) (2026-07-11)


### Features

* **import:** creer la participation VigieChiro des l import (best-effort) — Phase 1d-B ([#924](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/924)) ([0ea01b8](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0ea01b8489a45e03cb1f6dd8874788c0f9579fca)), closes [#900](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/900)

# [1.168.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.167.0...v1.168.0) (2026-07-11)


### Features

* **navigation:** avertir avant de fermer/quitter pendant une tâche longue ([#906](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/906)) ([b9ef1db](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/b9ef1db80431414bca440b7d7faec33550b746f4)), closes [#786](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/786)

# [1.167.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.166.1...v1.167.0) (2026-07-11)


### Features

* **passage:** passerelle SynchronisationParticipation + PATCH participation (Phase 1c/1b) ([#892](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/892)) ([0b08a3b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0b08a3be197b230e935b699d203c87303f0f9ce3)), closes [participations/#id](https://github.com/participations//issues/id)

## [1.166.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.166.0...v1.166.1) (2026-07-11)


### Bug Fixes

* main rouge (God Class SonsValidationController 203>200) → extraire ColonnesAudio ([#893](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/893)) ([9d0d9f4](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9d0d9f4f6206543fd8854bb424a759f09942eb24)), closes [#839](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/839) [#888](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/888)

# [1.166.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.165.0...v1.166.0) (2026-07-10)


### Features

* **api:** GET participation detaillee (_etag, meteo, config, traitement) — Phase 1a ([#883](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/883)) ([fe73484](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/fe73484b3de44453813d84201be3c21e28ade480)), closes [participations/#id](https://github.com/participations//issues/id)

# [1.165.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.164.2...v1.165.0) (2026-07-10)


### Features

* **lot:** declarer detecteur_enregistreur_type dans la config de participation (0d) ([#881](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/881)) ([61cf954](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/61cf954f585c26f8955234445cc9bfbdf1287198))

## [1.164.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.164.1...v1.164.2) (2026-07-10)


### Bug Fixes

* **import:** dater le passage mono-nuit d.apres la soiree des WAV (bug A) ([#877](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/877)) ([73b9dd7](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/73b9dd7f4ad34ff3458bee8a682c8e37c9b0b0c3))

## [1.164.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.164.0...v1.164.1) (2026-07-10)


### Bug Fixes

* **api:** création de participation refusée — retirer numero + remonter l'erreur réelle ([#861](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/861)) ([029f036](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/029f036a97e7128ee3c2820bcd857a899025d50a))

# [1.164.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.163.0...v1.164.0) (2026-07-10)


### Features

* **audio:** rattacher un passage à une participation avant import (axe 4.2) ([#854](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/854)) ([67476cf](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/67476cff388bfa1e81e5d1a9b9b1f9fc3c1a74a7))

# [1.163.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.162.0...v1.163.0) (2026-07-10)


### Features

* **api:** lister les participations + rattacher un passage à la main (axe 4.2) ([#850](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/850)) ([c578b5e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/c578b5e23f66fdf277aa79cc84fb3dfb54a3069d))

# [1.162.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.161.0...v1.162.0) (2026-07-10)


### Features

* **audio:** action « Importer depuis VigieChiro » dans « Sons & validation » (axe 4.2) ([#839](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/839)) ([8586c0e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/8586c0ee1035f0dcf14682d95848587967a9571c))

# [1.161.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.160.0...v1.161.0) (2026-07-10)


### Features

* **audio:** service + viewmodel d'import des résultats VigieChiro (axe 4.2) ([#834](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/834)) ([62c48c9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/62c48c916e37887b3c11e8b14a8bb22ee7ca4a2b)), closes [#142](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/142)

# [1.160.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.159.0...v1.160.0) (2026-07-10)


### Features

* **lot:** mémoriser le lien passage → participation au dépôt (axe 4.2) ([#831](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/831)) ([44b49a0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/44b49a0b60aecbd1adddce41d0e28c1ed8dbdd1a)), closes [#142](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/142)

# [1.159.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.158.0...v1.159.0) (2026-07-10)


### Features

* **lot:** action « Téléverser sur VigieChiro » à l'écran M-Lot ([#142](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/142)) ([#829](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/829)) ([513998f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/513998fbf0f556f702695b3d0750d08a9c92aa89))

# [1.158.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.157.0...v1.158.0) (2026-07-10)


### Features

* **lot:** coordination du téléversement d'une nuit sur VigieChiro (viewmodel + DI) ([#825](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/825)) ([7fe9420](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/7fe94202349e352cb377efe3b1747c411e8c7928)), closes [#142](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/142)

# [1.157.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.156.0...v1.157.0) (2026-07-10)


### Features

* **lot:** service d'orchestration du dépôt d'une nuit sur VigieChiro ([#815](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/815)) ([3b264eb](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/3b264eba64b2166951b9c5bc74ef5527b7172b1d)), closes [#142](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/142) [#702](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/702) [#697](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/697)

# [1.156.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.155.0...v1.156.0) (2026-07-10)


### Features

* **api:** écritures VigieChiro pour le dépôt d'une nuit (participation + fichiers) ([#811](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/811)) ([db4db24](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/db4db248b8340eceddf47aa2ea73672f2f39bf25)), closes [#142](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/142)

# [1.155.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.154.0...v1.155.0) (2026-07-10)


### Features

* **validation:** importer les résultats Tadarida d'une participation VigieChiro ([#807](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/807)) ([0ab0e5b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0ab0e5b2733ae2d4115b98316b04b88dedd7bfd7)), closes [#719](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/719)

# [1.154.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.153.0...v1.154.0) (2026-07-09)


### Features

* **design:** Chantier 4 — cartes consolidées + fond unique ([#694](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/694), [#687](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/687)) ([#777](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/777)) ([39bcadd](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/39bcaddfb5f918c97035addf024a19b169afbca9)), closes [#f5f6f8](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/f5f6f8) [688-#692](https://github.com/688-/issues/692)

# [1.153.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.152.0...v1.153.0) (2026-07-09)


### Features

* **design:** consolider les briques carte (bandeau-infos, carte-section, carte-stat) ([#694](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/694)) ([eeb5861](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/eeb5861b2c687e97a04be0526431ef7357dbe95c))

# [1.152.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.151.2...v1.152.0) (2026-07-09)


### Features

* **design:** en-têtes épurés — fiche site, passage, qualification ([#693](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/693)) ([#767](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/767)) ([ef9f7b3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ef9f7b33d39595f3cb6f3b943098ba4adb0439c4)), closes [#496](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/496)

## [1.151.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.151.1...v1.151.2) (2026-07-09)


### Bug Fixes

* **qualification:** appliquer l'expansion ×10 à la vue audio (axe des fréquences réel) ([#768](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/768)) ([5cd4dd1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5cd4dd1a5c06a2206a7e544742a9ef0aa03d020e)), closes [#762](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/762)

## [1.151.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.151.0...v1.151.1) (2026-07-09)


### Bug Fixes

* **import:** importer les bruts PR déjà expansés ×10 sans double expansion ([#762](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/762)) ([4bf42be](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/4bf42bedb916c5653059356d322567df15ce0b9d)), closes [#458](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/458)

# [1.151.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.150.0...v1.151.0) (2026-07-09)


### Features

* **design:** en-têtes épurés, M-Diagnostic + M-Import ([#693](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/693)) ([#763](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/763)) ([a5482ad](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/a5482ad0f35dc6c8bade41d621055b020bde35ed)), closes [#496](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/496)

# [1.150.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.149.0...v1.150.0) (2026-07-09)


### Features

* **design:** en-tête retiré + statut en barre de statut, M-Lot ([#693](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/693)) ([#760](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/760)) ([f6d1100](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/f6d11001bbcda65f094ae8c22f3b9b0b9a1bb7cf)), closes [#496](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/496)

# [1.149.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.148.0...v1.149.0) (2026-07-09)


### Features

* **design:** en-tête épuré + résumé en barre de statut, pilote Mes sites ([#693](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/693)) ([#756](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/756)) ([1c52530](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/1c5253007cd19809b890e64022d943d3654a32cb)), closes [#496](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/496) [#494](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/494) [#495](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/495) [#496](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/496)

# [1.148.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.147.0...v1.148.0) (2026-07-09)


### Features

* **audio:** filtre « Douteux » + lot + export ([#160](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/160), 2/2) ([#752](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/752)) ([66524c3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/66524c334ca4c9b3301ba4b1857fed5cd7e01885))

# [1.147.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.146.0...v1.147.0) (2026-07-09)


### Features

* **design:** badge de statut unifié dans les tables ([#691](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/691)) ([#750](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/750)) ([1db7f51](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/1db7f5132a54f47b8d6bc65821ca5f3dcc986367))

# [1.146.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.145.0...v1.146.0) (2026-07-09)


### Features

* **audio:** marquer une observation « douteuse » ([#160](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/160), 1/2) ([#747](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/747)) ([9756436](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/97564361715c9d60de7f616294226b0495876ed3)), closes [#478](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/478)

# [1.145.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.144.0...v1.145.0) (2026-07-09)


### Features

* **design:** densité de table unique via TableDonnees ([#690](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/690)) ([#743](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/743)) ([4473440](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/44734400c00e90b8223b5f54ca37d6de91658946))

# [1.144.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.143.0...v1.144.0) (2026-07-09)


### Features

* **vues:** vues par défaut sur Analyse et Multisite ([#735](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/735)) ([757db16](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/757db16d079ef6f40f4d57796c8c42d621c72058)), closes [#695](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/695)

# [1.143.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.142.0...v1.143.0) (2026-07-08)


### Features

* **statut:** barre de statut à 3 zones, masquée si vide ([#495](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/495)) ([#726](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/726)) ([ad641c5](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ad641c53622d316864ea76e982ac20a40405930d)), closes [#494](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/494)

# [1.142.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.141.0...v1.142.0) (2026-07-08)


### Features

* **lot:** primaire dynamique suivant l'étape courante ([#689](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/689)) ([#710](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/710)) ([6f0f114](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/6f0f1140ef2e5923b1beac5dd86d1024de0bb6d9))

# [1.141.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.140.0...v1.141.0) (2026-07-08)


### Features

* **audio:** vue par défaut « Sons non identifiés » (1/2 : la vue) ([#707](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/707)) ([353d00f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/353d00f40acd3ac694272ff1b1ebca9f8c587b4a))

# [1.140.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.139.1...v1.140.0) (2026-07-08)


### Features

* **design:** échelle de boutons unique ([#689](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/689), PR 2/2) ([#705](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/705)) ([5f242b7](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5f242b7b87700a341b4bc66c69b89a9b79529e0b)), closes [#6a737d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/6a737d)

## [1.139.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.139.0...v1.139.1) (2026-07-08)


### Bug Fixes

* **vues:** icônes d'onglet en FontIcon Ikonli (l'emoji 💾 ne se rendait pas) ([#700](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/700)) ([2cf8317](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/2cf83178129524fd69d63f9b677d898dfef9a9e0))

# [1.139.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.138.0...v1.139.0) (2026-07-08)


### Features

* **design:** socle de design partagé + jetons (chantier 0, [#688](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/688)) ([#698](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/698)) ([905b99f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/905b99fd7456e26213c7159dc2aa52d01921a52d)), closes [#685](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/685) [#494](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/494) [#689](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/689) [#34495e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/34495e)

# [1.138.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.137.1...v1.138.0) (2026-07-08)


### Features

* **vues:** vues par défaut en lecture seule + toujours une vue active ([c24c4e4](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/c24c4e44ec6b60582949e684b98e9e2e23c4af03)), closes [#623](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/623) [#471](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/471) [#484](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/484)

## [1.137.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.137.0...v1.137.1) (2026-07-08)


### Bug Fixes

* **vues:** détecter l'ajout d'une puce dans l'indicateur « modifié » ([503cd99](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/503cd99f5a060073aee40db66e5c9317014647c5)), closes [#681](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/681)

# [1.137.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.136.0...v1.137.0) (2026-07-08)


### Features

* valider à la main les enregistrements « non identifiés » (PR 2/2) ([#680](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/680)) ([10882d1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/10882d1a3e73656aeeda5d3ae69ca64ca3424cbb))

# [1.136.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.135.4...v1.136.0) (2026-07-08)


### Features

* **vues:** signaler une vue modifiée et l'enregistrer explicitement (socle onglets) ([875584b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/875584b7146273f76082548b9ebecc565350b43b)), closes [#623](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/623)

## [1.135.4](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.135.3...v1.135.4) (2026-07-08)


### Bug Fixes

* **import:** recadrer le bandeau « mélange » pour le cas multi-nuits ([#677](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/677)) ([4117b11](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/4117b111eebbc6ac28d002c1970674a5a5f4cede))

## [1.135.3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.135.2...v1.135.3) (2026-07-08)


### Bug Fixes

* **build:** recopier les ressources après la compilation pour fiabiliser javafx:run ([17c7a1b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/17c7a1bef257310ead051b3cb9052cc2ac527718))

## [1.135.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.135.1...v1.135.2) (2026-07-08)


### Bug Fixes

* **import:** table des nuits (hauteur, numérotation, cohérence) + capture ([#675](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/675)) ([5a56dcd](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5a56dcd903727715c55d52fa5bb5d34cfcdc677b))

## [1.135.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.135.0...v1.135.1) (2026-07-08)


### Bug Fixes

* **audio:** centrer verticalement l'éditeur du filtre « Heure » ([19f6296](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/19f62960479b4c94137165938148012c4235eabf))

# [1.135.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.134.2...v1.135.0) (2026-07-08)


### Features

* écouter les enregistrements « non identifiés » d’un passage (PR 1/2) ([#670](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/670)) ([3ec9c49](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/3ec9c49a06d1023be050425e589d881423b3def8))

## [1.134.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.134.1...v1.134.2) (2026-07-08)


### Bug Fixes

* **ihm:** aligner horizontalement les puces de filtres des barres « à la Notion » ([0dc7e6f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0dc7e6f0424aed58ab73bf9ac364c3a365335f0b))

## [1.134.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.134.0...v1.134.1) (2026-07-08)


### Bug Fixes

* **sites:** accepter les codes de point à plusieurs chiffres (Z41…) ([#668](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/668)) ([1d97079](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/1d97079b3d06c48e893962becb9f2baf65d4bcfb))

# [1.134.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.133.0...v1.134.0) (2026-07-07)


### Features

* **audio:** « Voir sur la carte » — rouvrir l'analyse filtrée depuis la revue ([#476](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/476)) ([e0f26dc](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e0f26dc87d94463ccd9d3ab3a11556eacad4656a)), closes [#537](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/537) [#467](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/467)

# [1.133.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.132.0...v1.133.0) (2026-07-07)


### Features

* **import:** découper l'import d'une carte SD en plusieurs nuits (un passage par nuit) ([#664](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/664)) ([9f147bb](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9f147bb60187aa57e58a705cd374c65551823db9)), closes [R5/#147](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/147) [#147](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/147) [#139](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/139)

# [1.132.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.131.0...v1.132.0) (2026-07-07)


### Features

* **audio:** onglets de vues mémorisées « à la Notion » ([#623](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/623)) ([#661](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/661)) ([bdbefa3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/bdbefa3c73e8dd8a4df30ae4db41efdf58115f95)), closes [#537](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/537)

# [1.131.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.130.0...v1.131.0) (2026-07-07)


### Features

* **cli:** exporter les observations d'un passage en CSV ([#659](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/659)) ([5fa31a9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5fa31a9c0eda245379608182debe8f0a19be81bb)), closes [#149](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/149) [#619](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/619)

# [1.130.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.129.0...v1.130.0) (2026-07-07)


### Features

* **analyse:** onglets de vues mémorisées « à la Notion » ([#623](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/623)) ([#657](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/657)) ([d06fe84](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d06fe843e0cb941141cea09d673ed887e804739c)), closes [#537](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/537)
* **cli:** clôturer le dépôt d'un passage (qualifier + deposer) ([#617](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/617)) ([2056222](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/2056222a188211e60a4389ea47decc32c0225ced))

# [1.129.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.128.0...v1.129.0) (2026-07-07)


### Features

* **cli:** importer les résultats Tadarida (CSV) ([#616](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/616)) ([dfe4ffa](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/dfe4ffa2c1a3409adeac24034b438a174a43333c))

# [1.128.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.127.0...v1.128.0) (2026-07-07)


### Features

* **cli:** provisionner sites et points d'écoute ([#615](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/615)) ([45b1bfa](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/45b1bfa09ff7b5c208fcce46490ee3a0d9101048))
* **purge:** purger les originaux déjà importés (par nuit + globale) ([#651](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/651)) ([54d2550](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/54d2550164eb6ecac8e2ff39fba83cff1a0bb1b5)), closes [#641](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/641)

# [1.127.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.126.0...v1.127.0) (2026-07-07)


### Features

* **cli:** inspecter un passage en lecture seule (statut-passage) ([#618](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/618)) ([651becb](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/651becb4840c06387b1c3d5e86c5902d05df670a))

# [1.126.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.125.0...v1.126.0) (2026-07-07)


### Features

* **import:** conservation optionnelle des originaux ([#641](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/641)) ([7619f9e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/7619f9eab2c7800b48ed18623dcfd37c24ca0954))

# [1.125.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.124.0...v1.125.0) (2026-07-07)


### Features

* **commun:** onglets de vues « à la Notion » (GestionnaireVues + DepotVues) ([#623](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/623)) ([da0bff8](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/da0bff8a333ec624deb199d3055d273eb819db81)), closes [#638](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/638) [#537](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/537)

# [1.124.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.123.0...v1.124.0) (2026-07-07)


### Features

* **commun:** socle des vues mémorisées (Gson + persistance + restauration) ([#623](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/623)) ([ffbe598](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ffbe59804229f26ae8fe677f8f7e58752b620f3c)), closes [#537](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/537)

# [1.123.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.122.0...v1.123.0) (2026-07-07)


### Features

* **export:** exporter les observations affichées en CSV ([#149](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/149)) ([0678b0f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0678b0fd88ea75a5ae9e25b260cda7e31833b56a)), closes [#618](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/618) [#537](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/537)

# [1.122.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.121.0...v1.122.0) (2026-07-07)


### Features

* **analyse:** barre de filtres « à la Notion » sur le socle partagé ([#622](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/622)) ([5634a4c](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5634a4c50e6832ee16dd43f3b817180b6afccfbf)), closes [#537](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/537) [#518](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/518) [#537](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/537)

# [1.121.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.120.0...v1.121.0) (2026-07-07)


### Features

* **data:** sauvegarde automatique avant un écrasement d'import ([#148](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/148)) ([1ea7158](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/1ea715863c3099bdc959c11dead98a789508afa7))

# [1.120.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.119.0...v1.120.0) (2026-07-07)


### Features

* **data:** menu Sauvegarder / Restaurer la base dans le chrome ([#148](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/148) partie 2/2) ([f317dff](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/f317dff7628106981d5b307b27ed39a0ee9a5243))

# [1.119.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.118.0...v1.119.0) (2026-07-07)


### Features

* **data:** service de sauvegarde et restauration de la base ([#148](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/148) partie 1/2) ([5c153c3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5c153c376dc61e1dd74f192c373f6937f7aa81a1))

# [1.118.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.117.0...v1.118.0) (2026-07-07)


### Features

* **multisite:** distance entre points et garde-fou de proximité ([#154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/154)) ([7865752](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/78657524ee7c7266e5f03a9c00a622e62adb106d))

# [1.117.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.116.0...v1.117.0) (2026-07-07)


### Features

* **saisie:** coordonnées GPS multi-format DD/DMS avec validation ([#153](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/153)) ([61b0d1a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/61b0d1a37d7a3b5dcf0193cfc30e2b3de2b467da)), closes [#615](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/615)

# [1.116.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.115.0...v1.116.0) (2026-07-07)


### Features

* **analyse:** filtres unifies sur le socle + Taxon parent ([#537](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/537) 4b, [#518](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/518)) ([#612](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/612)) ([5b28054](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5b2805420a3ac021707402e9bebe0cdfe549259b))

# [1.115.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.114.0...v1.115.0) (2026-07-06)


### Features

* **filtres:** descripteur de filtre transportable ([#537](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/537) etape 2/5) ([#606](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/606)) ([9857fa9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9857fa9b808ef371a4e645df8b0d9a75cca0d40d)), closes [#476](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/476) [#484](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/484)

# [1.114.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.113.0...v1.114.0) (2026-07-06)


### Features

* **captures:** résultats « Espèces » de la recherche + illustrer annuler le dépôt ([#600](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/600)) ([13d6f7c](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/13d6f7cf2eb680f93e3d8ad51c764ad90b77248d)), closes [#323](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/323) [#460](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/460)

# [1.113.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.112.0...v1.113.0) (2026-07-05)


### Features

* **captures:** sélection multiple + éditeur de commentaire (Sons & validation) ([#598](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/598)) ([56b5ce9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/56b5ce9c1fca323fcc2804091a77c8aaa4f4f6ac)), closes [#479](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/479) [#477](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/477)

# [1.112.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.111.0...v1.112.0) (2026-07-05)


### Features

* **audio:** filtre Heure — defaut = coucher/lever du soleil du passage ([#549](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/549)) ([#594](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/594)) ([d73b458](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d73b4583e7bbce03ba8cd35d50920621e376a22e))

# [1.111.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.110.0...v1.111.0) (2026-07-05)


### Features

* **captures:** dialogues programmatiques (confirmations, modales de saisie) ([#526](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/526)) ([#593](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/593)) ([9490dda](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9490dda46d9d735e7b8fb253548c057778141bff)), closes [#534](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/534) [#147](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/147) [#279](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/279) [#326](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/326) [#30](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/30) [#178](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/178)

# [1.110.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.109.0...v1.110.0) (2026-07-05)


### Features

* **captures:** tableau replié / carte plein écran de Carte & passages ([#527](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/527)) ([#591](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/591)) ([9bec5f0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9bec5f0821217bd816f6a8b61b839ebbf87e58e5)), closes [#347](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/347)

# [1.109.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.108.0...v1.109.0) (2026-07-05)


### Features

* **diagnostic:** cohérence horaires (nuit réelle vs enregistrement) ([#548](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/548)) ([#589](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/589)) ([245f936](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/245f93639ad2d531cee5f841a873526339107365))

# [1.108.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.107.0...v1.108.0) (2026-07-05)


### Features

* **captures:** aperçus de la vue audio (filtres actifs, colonnes de mesures) ([#519](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/519)) ([#586](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/586)) ([e00723a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e00723a5e7751e77679dd7b888779227a550883f)), closes [471/#512](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/512) [491/#500](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/500)

# [1.107.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.106.0...v1.107.0) (2026-07-05)


### Features

* **commun:** ephemeride solaire locale NOAA (lever/coucher) ([#548](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/548)) ([#585](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/585)) ([889d428](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/889d4284001270311392dd3dae2d63334a9c3c83))

# [1.106.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.105.0...v1.106.0) (2026-07-05)


### Features

* **passage:** pre-remplir la meteo d un passage depuis Open-Meteo ([#547](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/547)) ([#583](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/583)) ([36331fa](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/36331fac38d53b83361c2e3a3dd7ff16b86d1f97))

# [1.105.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.104.0...v1.105.0) (2026-07-05)


### Features

* **passage:** client Open-Meteo (P4a, offline-friendly) ([#572](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/572)) ([47de721](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/47de72111b580e449619ae9a1a2fe4a295fff81c)), closes [#547](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/547) [#543](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/543)

# [1.104.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.103.0...v1.104.0) (2026-07-05)


### Features

* **audio:** mémoriser les filtres entre deux ouvertures ([#484](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/484) partie 2/2) ([#568](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/568)) ([d37f1a2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d37f1a2c25a3752eef728a08bbb94705193e8d67))
* **passage:** saisie du materiel du micro (P3b, ferme [#546](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/546)) ([#569](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/569)) ([84e7917](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/84e7917f644bc8f387cd3d9f148a25e096fa32db)), closes [#543](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/543)

# [1.103.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.102.0...v1.103.0) (2026-07-05)


### Features

* **audio:** mémoriser le tri de la table entre deux ouvertures ([#484](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/484) partie 1/2) ([#566](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/566)) ([8809bd0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/8809bd0e12e51d3fd449a157cc985fd4490429d3))

# [1.102.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.101.0...v1.102.0) (2026-07-05)


### Features

* **audio:** multi-sélection et actions groupées dans la vue ([#479](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/479) partie 3/3) ([#563](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/563)) ([29eddc0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/29eddc0b3a3a3185751a70a4ef98ee60aa28015e))

# [1.101.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.100.0...v1.101.0) (2026-07-05)


### Features

* **passage:** saisie du releve meteo dans la vue Passage (P3a) ([#562](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/562)) ([681e015](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/681e015ec9715c6f5e226f48d8efd9731048d65a)), closes [#106](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/106) [#546](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/546) [#546](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/546) [#543](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/543)

# [1.100.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.99.0...v1.100.0) (2026-07-05)


### Features

* **audio:** actions de revue en lot dans le view-model ([#479](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/479) partie 2/3) ([#560](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/560)) ([4388caf](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/4388cafcb1d7b0c49b2fb8c09a563ea8cd9bd94c)), closes [#558](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/558)

# [1.99.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.98.0...v1.99.0) (2026-07-05)


### Features

* **validation:** actions de revue en lot atomiques ([#479](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/479) partie 1/3) ([#558](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/558)) ([de3b207](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/de3b2079eb7c2d55db09b758239649dcb4d9ef30))

# [1.98.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.97.0...v1.98.0) (2026-07-05)


### Features

* **audio:** revue au clavier (Entrée / R / N) ([#478](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/478)) ([#555](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/555)) ([47b80b4](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/47b80b46f047153a2bd98ef789ac794f292bfad1))

# [1.97.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.96.0...v1.97.0) (2026-07-05)


### Features

* **passage:** materiel du micro (position, hauteur, type) — modele + DAO ([#556](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/556)) ([a0a734e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/a0a734e77b1f4ecfe266b86da36bcad4ba8ce60f))

# [1.96.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.95.0...v1.96.0) (2026-07-05)


### Features

* **audio:** lecture automatique à la sélection + boucle ([#483](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/483)) ([#552](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/552)) ([6bf3cd9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/6bf3cd9a537150378f16484eaaec259df703045f))

# [1.95.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.94.0...v1.95.0) (2026-07-05)


### Features

* **passage:** modele meteo etendue (temp fin, vent, couverture nuageuse) ([#551](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/551)) ([52693bc](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/52693bc90dfb97a50e0a1cbaa9dcdfef76a6772f)), closes [#106](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/106) [#546](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/546) [#547](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/547) [#543](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/543)

# [1.94.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.93.0...v1.94.0) (2026-07-05)


### Features

* **audio:** filtre par plage horaire (écarter les heures de jour, gère minuit) ([#531](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/531)) ([#542](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/542)) ([d6a25e9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d6a25e98f80b80ce503cb58fb2482f570ce99cda)), closes [#474](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/474) [#530](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/530) [#467](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/467) [#530](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/530)

# [1.93.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.92.0...v1.93.0) (2026-07-05)


### Features

* **audio:** colonne « Heure » triable (chronologique, gère minuit) ([#530](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/530) partie 2/2) ([#541](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/541)) ([d5fdbd9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d5fdbd97ef1c0aeffe54411450a294022e06ab7c)), closes [#531](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/531)

# [1.92.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.91.0...v1.92.0) (2026-07-05)


### Features

* **passage:** socle heure de capture — extraction à l import + persistance ([#530](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/530) partie 1/2) ([#539](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/539)) ([5f6044b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5f6044bd2d50527487e56ec55451ba4baf953622))

# [1.91.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.90.0...v1.91.0) (2026-07-05)


### Features

* **audio:** édition inline du commentaire (clic sur la case) ([#536](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/536)) ([a4191e0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/a4191e047c2f07e790c17d37a84790804c398d59)), closes [#477](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/477)

# [1.90.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.89.0...v1.90.0) (2026-07-05)


### Features

* **captures:** capture hors-écran des Dialog et DialogPane ([#534](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/534)) ([936775c](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/936775cf417d4ec3d770f9b4068f172f956650aa))

# [1.89.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.88.0...v1.89.0) (2026-07-05)


### Features

* **audio:** socle édition du commentaire (service + view-model) ([#532](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/532)) ([5d60d8f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5d60d8f3861a10ee62fee5341b64827ba737d15a)), closes [#477](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/477) [#477](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/477)

# [1.88.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.87.0...v1.88.0) (2026-07-05)


### Features

* **audio:** filtre par seuil de probabilité dans la barre Notion ([#525](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/525)) ([b5f6a99](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/b5f6a99fe642842c5587e920a415f3e4f7910dfe)), closes [#474](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/474) [#467](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/467)

# [1.87.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.86.0...v1.87.0) (2026-07-05)


### Features

* **audio:** filtre « Références seulement » dans la barre Notion ([#520](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/520)) ([e5a5188](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e5a51882b9a8fb44a8380c59313e86d7c06de2f7)), closes [#473](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/473)

# [1.86.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.85.0...v1.86.0) (2026-07-05)


### Features

* **audio:** filtre par espèce (taxon) dans la barre Notion ([#515](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/515)) ([7c6dade](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/7c6dade3708956bdd3febac6e0dad774e20e51a3)), closes [#472](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/472)

# [1.85.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.84.1...v1.85.0) (2026-07-05)


### Features

* **audio:** filtre par groupe taxonomique (Chiroptères, Oiseaux…) dans la barre Notion ([#512](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/512)) ([38dc1f2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/38dc1f29abe3f4d800aca346e94e6f0f2c69998b)), closes [#507](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/507) [#471](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/471)

## [1.84.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.84.0...v1.84.1) (2026-07-05)


### Bug Fixes

* **captures:** reparer CaptureImport (binding CompteurValidations manquant) ([#509](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/509)) ([07a8b07](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/07a8b07f1649bcf7c672da2a675c2b01412e9870))

# [1.84.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.83.0...v1.84.0) (2026-07-05)


### Features

* **audio:** filtres « à la Notion » — socle composable + barre (statut + recherche) ([#470](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/470)) ([#506](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/506)) ([ef52041](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ef5204194695ae196b0fc2964c0fd5eddf4284d1)), closes [471-#475](https://github.com/471-/issues/475) [470/#471](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/471)

# [1.83.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.82.0...v1.83.0) (2026-07-05)


### Features

* taxon parent dans les vues centrees observation ([#507](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/507)) ([25b30ef](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/25b30ef829a0f9b0b578cb988bb0382879dc6b27))

# [1.82.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.81.2...v1.82.0) (2026-07-04)


### Features

* **audio:** colonnes FME & fréquence terminale (grandeurs d'identification) ([#500](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/500)) ([#505](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/505)) ([c03db0f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/c03db0f8e0fa0c3ceff942d7409c94c740b7e816))

## [1.81.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.81.1...v1.81.2) (2026-07-04)


### Bug Fixes

* temps du cri en secondes réelles + découpage à 5 s réelles (fidèle à Tadarida) ([#504](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/504)) ([4cc2455](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/4cc245580b107219c26ded0438d3c74f4d361add)), closes [501/#482](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/482)

## [1.81.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.81.0...v1.81.1) (2026-07-04)


### Bug Fixes

* **audio:** fréquence médiane en kHz (et non en Hz) ([#503](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/503)) ([6f4afbc](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/6f4afbc255b87fcd2cd421f5cc3b62b0df3e123e)), closes [#502](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/502)

# [1.81.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.80.0...v1.81.0) (2026-07-04)


### Features

* **audio:** colonne « Début » (position réelle du cri dans le fichier) ([#501](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/501)) ([5a46639](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5a4663999c3841cd6197a6ba6ca49a56f5352550)), closes [#482](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/482) [#51](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/51) [#52](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/52)
* **audio:** seek + surlignage de la fenêtre du cri sélectionné ([#482](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/482)) ([#502](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/502)) ([7544fbf](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/7544fbf399b06dd4618f72e0862f355631f4235f)), closes [51/#52](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/52)

# [1.80.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.79.0...v1.80.0) (2026-07-04)


### Features

* **audio:** colonne « Durée » du cri (discriminant plus utile que la fréquence médiane) ([#467](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/467)) ([#499](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/499)) ([165ce33](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/165ce336ac6509836e2e7c55f9e7d66a64e833ed)), closes [#482](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/482)

# [1.79.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.78.0...v1.79.0) (2026-07-04)


### Features

* **audio:** retirer la ligne de détail redondante du panneau d'écoute ([#493](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/493)) ([#498](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/498)) ([8da64ff](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/8da64ff2bd1848637e000d1669a290ef2b6d7bef)), closes [#495](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/495)

# [1.78.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.77.0...v1.78.0) (2026-07-04)


### Features

* **audio:** optimiser l'espace vertical — titre retiré + résumé en barre de statut ([#493](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/493)) ([#497](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/497)) ([11eb714](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/11eb71443ff36728a39010393f4fa2a7effa940a)), closes [#494](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/494)

# [1.77.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.76.0...v1.77.0) (2026-07-04)


### Features

* réorganiser les colonnes (glisser, façon Notion) — composant réutilisable ([#491](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/491)) ([#492](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/492)) ([1e5699a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/1e5699a09e97d18ecfd2037ce4eb7c37f5c5ce0f))

# [1.76.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.75.0...v1.76.0) (2026-07-04)


### Features

* **audio:** sélecteur d'affichage des colonnes ([#481](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/481)) ([#490](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/490)) ([699fde3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/699fde39a357cb336328c8cfc89c6158df5c0a54))

# [1.75.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.74.0...v1.75.0) (2026-07-04)


### Features

* **audio:** colonnes Date, Fréquence et indicateur de commentaire ([#480](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/480)) ([#489](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/489)) ([3f1192b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/3f1192be207820dc6a03b2174b26a93e42dbd0be))

# [1.74.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.73.0...v1.74.0) (2026-07-04)


### Features

* **audio:** colonnes triables ([#469](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/469)) ([#486](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/486)) ([17db85b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/17db85b7da1a4efe58a41f9205ed07a6eb864547)), closes [#487](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/487) [#488](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/488) [#487](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/487)

# [1.73.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.72.0...v1.73.0) (2026-07-04)


### Features

* **audio:** colonne « Fichier » dans la table de revue ([#468](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/468)) ([#485](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/485)) ([9268753](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/92687532ac12a9fa22228c03e83097343c8fc2f4))

# [1.72.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.71.0...v1.72.0) (2026-07-04)


### Features

* alerter du nombre de validations menacées avant une opération destructive ([#466](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/466)) ([73fd3e1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/73fd3e1d940b822e14195d753faae2051f375da7))

# [1.71.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.70.1...v1.71.0) (2026-07-02)


### Features

* **validation:** préserver les validations observateur à la ré-importation CSV ([#463](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/463)) ([676e066](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/676e06685fba9cd0d0384040bdad4798c0698f16))

## [1.70.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.70.0...v1.70.1) (2026-07-02)


### Bug Fixes

* **import:** purge des temporaires zip au démarrage + message « disque plein » explicite ([#462](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/462)) ([88ec1c0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/88ec1c05a41b43e78900481a855a162e2d36971f))

# [1.70.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.69.0...v1.70.0) (2026-07-02)


### Features

* **passage:** annuler le dépôt d'un passage (validations conservées) ([#460](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/460)) ([9cee497](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9cee497a67b983f4665a60bc8c9fad7e0eb5f1de))

# [1.69.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.68.0...v1.69.0) (2026-07-02)


### Features

* **import:** rejeter et prévenir les enregistrements déjà ralentis (garde-fou double expansion) ([#458](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/458)) ([76c27be](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/76c27be0f0469e5b5ceb08c6c28b51704266650e)), closes [#155](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/155)

# [1.68.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.67.0...v1.68.0) (2026-07-02)


### Features

* **audio:** normalisation visuelle du spectrogramme + axes en fréquences réelles ([#456](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/456)) ([8c99c32](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/8c99c32c038cc8592ae3d13b10498c3f4febff19))

# [1.67.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.66.0...v1.67.0) (2026-07-02)


### Features

* **audio:** activer la normalisation visuelle du sonogramme (audio-view 1.13.1) ([#444](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/444)) ([b27752f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/b27752f83d9bd1ad1684929c091fbb7ea0f45cac))

# [1.66.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.65.1...v1.66.0) (2026-07-01)


### Features

* **audio:** deux colonnes de taxon (Tadarida vs votre décision) au lieu de trois ([#442](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/442)) ([8f4028b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/8f4028b984f153aea059bc2326e2c5ec16aa31da))

## [1.65.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.65.0...v1.65.1) (2026-06-30)


### Bug Fixes

* **validation:** réparer les taxons-souches masquant le référentiel officiel (V06) ([#440](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/440)) ([7437dc2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/7437dc216787c60bfe28037855271925f5faeec5)), closes [#437](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/437)

# [1.65.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.64.0...v1.65.0) (2026-06-30)


### Features

* **audio:** nom vernaculaire (espèce + Tadarida) et probabilité dans la table de validation ([#437](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/437)) ([a64cc06](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/a64cc06651a1f6e9a3d524be8c0003c373ddc599))

# [1.64.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.63.0...v1.64.0) (2026-06-30)


### Features

* **audio:** proposer la réimportation d'un CSV Tadarida + icône info lisible ([#436](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/436)) ([3fb0cc5](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/3fb0cc5ba59f3d9fedaac5cf4b12a58791aa20d8))

# [1.63.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.62.0...v1.63.0) (2026-06-30)


### Features

* **validation:** seed du référentiel officiel Tadarida (France) — V05 ([#435](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/435)) ([61a2a3f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/61a2a3fa85b216430ebe10df87f5ee6532119e8c))

# [1.62.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.61.1...v1.62.0) (2026-06-30)


### Features

* **audio:** import Tadarida tolérant (séquences manquantes + taxons hors référentiel) ([#432](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/432)) ([e424154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e42415441839139a5e1263f3b8bb9022d167afd9))

## [1.61.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.61.0...v1.61.1) (2026-06-30)


### Bug Fixes

* **audio:** bandeau de retour d'import bien plus visible + erreur séquence actionnable ([#431](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/431)) ([49e540a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/49e540a587f3b96682ecf62ef949cdce981872c5)), closes [#bandeauRetour](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/bandeauRetour) [#lblMessage](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/lblMessage)

# [1.61.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.60.0...v1.61.0) (2026-06-30)


### Features

* **audio:** glisser-déposer d'un CSV Tadarida (fallback FileChooser) ([#427](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/427)) ([ca40eec](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ca40eecda8035fcfdb493d7d11cee2654bd9a766))

# [1.60.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.59.2...v1.60.0) (2026-06-30)


### Features

* **audio:** retour d'information explicite pour l'import/export (fini le placeholder gris) ([#426](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/426)) ([d86dc11](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d86dc1112ce02567c49206fdb605846bc3468a02))

## [1.59.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.59.1...v1.59.2) (2026-06-30)


### Bug Fixes

* **validation:** tolère une probabilité textuelle dans un _Vu Tadarida (SUR) ([#425](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/425)) ([a85d35b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/a85d35b448af5a29b8d4d942bd8c4e8b304812fc))

## [1.59.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.59.0...v1.59.1) (2026-06-29)


### Bug Fixes

* **audio:** restaure progression de revue + option « inclure le mode » ([#423](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/423)) ([a319ef4](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/a319ef40e18910d76c0da59510ba95c93b5f5e85))

# [1.59.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.58.0...v1.59.0) (2026-06-29)


### Features

* **audio:** branchement multisite — ligne + lot filtré (PR-3d) ([#415](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/415)) ([70a63e0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/70a63e0c7a97c1c2e7efb01c53088a2f72f9bcc8)), closes [#291](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/291)

# [1.58.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.57.0...v1.58.0) (2026-06-29)


### Features

* **audio:** branchement analyse — source ParEspece + contrat OuvrirAnalyse (PR-3c) ([#413](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/413)) ([300e0ee](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/300e0eee68c795ed0b5852301b5c4b5e57b40868))

# [1.57.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.56.0...v1.57.0) (2026-06-29)


### Features

* **audio:** branchement passage — OuvrirValidation délègue à OuvrirAudio (PR-3b) ([#411](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/411)) ([bd0ffdb](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/bd0ffdb744806eefbad6eae523534452a9b55823))

# [1.56.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.55.0...v1.56.0) (2026-06-29)


### Features

* **audio:** écran Sons & validation + entrée Références (PR-3a) ([#409](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/409)) ([53bc874](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/53bc8748feb7113346331e9796d7f34a23a72f5e)), closes [#329](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/329)

# [1.55.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.54.0...v1.55.0) (2026-06-28)


### Features

* **audio:** SourceObservations + ViewModel audio unifié ([#407](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/407)) ([d7bb51d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d7bb51d26ccd12ddc6fdac3467cf38a9878ae43d)), closes [#404](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/404)

# [1.54.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.53.0...v1.54.0) (2026-06-28)


### Features

* **audio:** couche modèle de la vue audio unifiée (record + projections) ([#404](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/404)) ([924a395](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/924a39533fca8d414c4376564727be65feab1d5d)), closes [#audio](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/audio)

# [1.53.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.52.0...v1.53.0) (2026-06-28)


### Features

* **validation:** marquer référence + lister les références (socle vue audio unifiée) ([#402](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/402)) ([5f9ecc6](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5f9ecc616be54b26f10e9a865fe176524a2b1b09))

# [1.52.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.51.0...v1.52.0) (2026-06-28)


### Features

* **analyse:** carte de répartition (choroplèthe de richesse + répartition d'une espèce) ([#400](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/400)) ([5aaf510](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5aaf510ecf167ae2dca794eaed91713bcf2a71fd))

# [1.51.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.50.0...v1.51.0) (2026-06-28)


### Features

* **analyse:** exposer les carrés de l'espèce sélectionnée (données carte) ([#398](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/398)) ([ade69d2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ade69d294f613370f0c5781f9c86147fe47e9f5b))

# [1.50.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.49.1...v1.50.0) (2026-06-28)


### Features

* **accueil:** accueil à deux prismes (Collecte & passages / Espèces & biodiversité) ([#396](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/396)) ([f5f2e02](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/f5f2e027f76aa2715b68ab7a9107970fdbdd59fd))

## [1.49.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.49.0...v1.49.1) (2026-06-28)


### Bug Fixes

* **captures:** cohérence géographique carré ↔ coordonnées ([#392](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/392)) ([e335fc0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e335fc02bcd8094ac51094b2f68eed262919542c))

# [1.49.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.48.0...v1.49.0) (2026-06-28)


### Features

* **analyse:** écouter / valider une détection depuis « Espèces & observations » ([#388](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/388)) ([0b8abca](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0b8abca93a6df9c98af8decd14c70cc005242ce5))

# [1.48.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.47.0...v1.48.0) (2026-06-28)


### Features

* **recherche:** documenter la recherche globale (page, capture, README) ([#389](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/389)) ([acca4a9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/acca4a9b77a096feef5e9d271f842925be1b8ed9))

# [1.47.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.46.1...v1.47.0) (2026-06-28)


### Features

* **analyse:** détail des observations d'une espèce à travers les passages + ouvrir le passage ([#379](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/379)) ([d45333b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d45333b291e4868fa2330c9015b871c5c5509c81)), closes [#381](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/381)

## [1.46.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.46.0...v1.46.1) (2026-06-28)


### Bug Fixes

* **importation:** afficher la carte dans la capture de l'assistant d'import ([#383](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/383)) ([68ec523](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/68ec523aff9021a4b2e2df04f7c576c52d2b6a5e))

# [1.46.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.45.0...v1.46.0) (2026-06-28)


### Features

* **sites:** lien « placer sur la carte » pour les points sans GPS ([#380](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/380)) ([bc43439](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/bc434391fb5e9179a498acc4f0b9c98bb9e6704f))

# [1.45.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.44.0...v1.45.0) (2026-06-28)


### Features

* **multisite:** barre d'outils épurée (menu ☰, replis en bas, invites intégrées) ([#377](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/377)) ([e112def](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e112defafacd4dc4d06ddb3d6e5149624233e86a)), closes [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)

# [1.44.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.43.0...v1.44.0) (2026-06-28)


### Features

* **analyse:** filtre texte + export CSV de l'inventaire des espèces ([#375](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/375)) ([a856ab0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/a856ab0bf626bf7f6508b212e0ad32681e3006df))

# [1.43.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.42.0...v1.43.0) (2026-06-28)


### Features

* **accueil:** icône de carte pour « Carte & passages » ([#373](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/373)) ([10fbeff](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/10fbeffc93c15d93ac91ebbe7f87fe91ba11e172))

# [1.42.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.41.0...v1.42.0) (2026-06-28)


### Features

* **carte:** numéro de carré dans le coin + nom de point abrégé, contourés ([#371](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/371)) ([bdaeb99](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/bdaeb99a0be37bd3e223e7bd8104701f1ee6492e))

# [1.41.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.40.0...v1.41.0) (2026-06-28)


### Features

* **analyse:** écran « Espèces & observations » (inventaire pivot espèce/carré) ([#367](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/367)) ([91c6026](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/91c602639ff51b39ab61a93ee49554674e7d714c)), closes [#365](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/365) [#86](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/86) [#fafbfc](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/fafbfc)

# [1.40.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.39.0...v1.40.0) (2026-06-28)


### Features

* **multisite:** « Enregistrer » en overlay + capture & doc du mode édition ([#368](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/368)) ([9bc9933](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9bc9933de183c3188282003e5142c61945209c74)), closes [#154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/154)

# [1.39.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.38.0...v1.39.0) (2026-06-28)


### Features

* **analyse:** projections d'inventaire des espèces (par espèce / par carré) ([#365](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/365)) ([541a649](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/541a649abd9aac2e6006aac9c477503aaa4ddb79))
* **multisite:** toggle « Éditer les positions » en overlay icône sur la carte ([#362](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/362)) ([dae9131](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/dae913181300070080861f1cc31bfae57b410adb)), closes [#154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/154)

# [1.38.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.37.0...v1.38.0) (2026-06-28)


### Features

* **recherche:** inclure les espèces/observations dans la recherche globale ([#323](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/323)) ([#363](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/363)) ([75ba3ab](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/75ba3abf12909cf3312a33bffa357e0e8b5b4501)), closes [DaoGenerique#projeter](https://github.com/DaoGenerique/issues/projeter)

# [1.37.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.36.0...v1.37.0) (2026-06-28)


### Features

* carte de confirmation au rattachement d'import ([#154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/154)) ([#360](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/360)) ([5eb45b6](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5eb45b633a650facc32dc2fd06c862fd7d94ed8b))

# [1.36.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.35.0...v1.36.0) (2026-06-28)


### Features

* badge GPS de la fiche site → « voir sur la carte » du point ([#154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/154)) ([#358](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/358)) ([9e4eba6](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9e4eba692a2d6155f9d443ef01d230cd4f231457))

# [1.35.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.34.0...v1.35.0) (2026-06-28)


### Features

* **multisite:** barre d'outils sur deux rangées, lisible ([#340](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/340)) ([#356](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/356)) ([76f6848](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/76f6848ddf446c32ed13a8fdbf184ca3e03d6001)), closes [#154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/154)

# [1.34.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.33.0...v1.34.0) (2026-06-28)


### Features

* éditer les positions des points sur la carte multi-sites ([#154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/154)) ([#353](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/353)) ([d9b59da](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d9b59da3a4aafbb314e3881737cce895bec22315))
* **multisite:** renomme la vue en « Carte & passages » ([#342](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/342)) ([#354](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/354)) ([ea48f92](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ea48f923f2e4b7d952f7444792d914ed96e36707)), closes [#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152)

# [1.33.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.32.0...v1.33.0) (2026-06-28)


### Features

* **multisite:** retire le titre de page pour récupérer de la hauteur ([#341](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/341)) ([#351](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/351)) ([af515fc](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/af515fc60914b4c4c39bf6ca5214fb87aa479084)), closes [#lblResume](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/lblResume)

# [1.32.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.31.0...v1.32.0) (2026-06-28)


### Features

* **carte:** bouton « recadrer » sur la carte multisite ([#339](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/339)) ([#349](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/349)) ([c42710e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/c42710e40e12a07b7af32a3d7adac726a87eeebb)), closes [#345](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/345)

# [1.31.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.30.0...v1.31.0) (2026-06-28)


### Features

* carte-outil de saisie GPS dans la modale point ([#153](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/153)) ([#345](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/345)) ([9302c30](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9302c30643235ee6117c5a77714df73ac901a8f9))
* **multisite:** « Voir sur la carte » replie le tableau ([#338](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/338)) ([#347](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/347)) ([0ae770b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0ae770b87f7dfb86f97c9dc4d99689b02e40d369))

# [1.30.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.29.0...v1.30.0) (2026-06-28)


### Features

* **multisite:** légende de la carte repliée par défaut ([#337](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/337)) ([#344](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/344)) ([ecf12c3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ecf12c3b635f4c689c83366353202978a25aea61))

# [1.29.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.28.1...v1.29.0) (2026-06-28)


### Features

* afficher un point sans GPS au centre de son carré ([#153](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/153)) ([#336](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/336)) ([33ab9aa](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/33ab9aa82a7389a6bf79f4945ed8aaf3b61c61fc)), closes [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)

## [1.28.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.28.0...v1.28.1) (2026-06-27)


### Bug Fixes

* **captures:** CaptureEcrans rend le chrome complet (RechercheGlobale bindé) ([#334](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/334)) ([5d48eb2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5d48eb23de6bba01c578bdf7dec3c19531d58c22)), closes [#333](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/333) [#144](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/144)

# [1.28.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.27.0...v1.28.0) (2026-06-27)


### Features

* **carte:** boutons « Voir sur la carte » (M-Passage, fiche site) ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152)) ([0f4820b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0f4820b5aa311925064c176d9821d24f05039924))
* **carte:** contrat OuvrirMultisite + focaliserSur la carte multi-sites ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152)) ([1319bca](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/1319bca17adb2381df73af4c3b820ca6bcf02917))

# [1.27.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.26.2...v1.27.0) (2026-06-27)


### Features

* **carte:** focaliser + éditer un point — socle géo (PR-1) ([#330](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/330)) ([f47da26](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/f47da26896e9e5213961257a30b2298de6d221a3)), closes [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)

## [1.26.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.26.1...v1.26.2) (2026-06-27)


### Bug Fixes

* **multisite:** poignées de repli dans la barre d'actions ([#328](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/328)) ([#331](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/331)) ([92d529b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/92d529b07fca098def7343162db7d8afe4b003d7)), closes [#314](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/314)

## [1.26.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.26.0...v1.26.1) (2026-06-27)


### Bug Fixes

* **qualification:** la colonne détail défile au lieu que l'AudioView déborde ([#329](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/329)) ([e2a6126](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e2a6126341fa6ae6706e6c8837196d8ea2166a04))

# [1.26.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.25.0...v1.26.0) (2026-06-27)


### Features

* **carte:** carroyage national officiel + recalage du carré d'exemple ([#325](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/325)) ([#327](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/327)) ([ef01917](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ef0191715e0165a50a64f4cb70fbd226770271a9))

# [1.25.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.24.1...v1.25.0) (2026-06-27)


### Features

* **sites:** édition de la fiche site (bouton « Modifier ») ([#326](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/326)) ([efc3709](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/efc37093af3fbe3322187af3b9ed5a1b68e5a116))

## [1.24.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.24.0...v1.24.1) (2026-06-27)


### Bug Fixes

* **sites:** explique pourquoi « Supprimer » est grisé (tooltip) ([#320](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/320)) ([e73b8a9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e73b8a9548ce0bb8d8eb0acf9fb204f4afab6c41))

# [1.24.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.23.0...v1.24.0) (2026-06-27)


### Features

* **accessibilité:** nom accessible sur les cartes d'action M-Passage ([#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)) ([#319](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/319)) ([9692edd](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9692edda19496441f0dd589753044f8c878a491f))

# [1.23.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.22.0...v1.23.0) (2026-06-27)


### Features

* **recherche:** champ de recherche globale dans le chrome ([#144](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/144)) ([#314](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/314)) ([915e0ce](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/915e0ceab8314e568e37a6a1d85f72a4e26e1cd2))

# [1.22.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.21.0...v1.22.0) (2026-06-27)


### Features

* **recherche:** moteur de recherche globale — sites, points, passages ([#144](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/144)) ([#312](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/312)) ([1a9c083](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/1a9c0833244895835740b174c7847c854492b253))

# [1.21.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.20.0...v1.21.0) (2026-06-27)


### Features

* **multisite:** tableau de bord — mini-stats au survol ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152)) ([#309](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/309)) ([1fe2362](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/1fe23620763540cc862b215d245757eafeafa454)), closes [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)

# [1.20.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.19.0...v1.20.0) (2026-06-27)


### Features

* **multisite:** tableau de bord — densité par carré + légende ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152)) ([#304](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/304)) ([315f36a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/315f36ae6daaaf44f79dccfc354adaa8175f3b03)), closes [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163) [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)

# [1.19.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.18.0...v1.19.0) (2026-06-27)


### Features

* **multisite:** liaisons carte ↔ tableau ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152), PR 2-4) ([#301](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/301)) ([af4e85e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/af4e85e4d4e9c4d688fc9e385d8d75a152470aae)), closes [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)

# [1.18.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.17.2...v1.18.0) (2026-06-27)


### Features

* **multisite:** vue carte + tableau ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152), PR 2-3) ([#294](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/294)) ([198f8f2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/198f8f2f869a9070a053e6042d086a54629d53ce))

## [1.17.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.17.1...v1.17.2) (2026-06-27)


### Bug Fixes

* **multisite:** l'export CSV suit le tri par clic d'en-tête (suivi de [#291](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/291)) ([#295](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/295)) ([ad0557c](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ad0557cc0f3661dd1d941b6f9d41439d06f4c5f1))

## [1.17.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.17.0...v1.17.1) (2026-06-27)


### Performance Improvements

* **carte:** séparer le rafraîchissement carte du tableau ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152), suivi de [#289](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/289)) ([#292](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/292)) ([76e1831](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/76e183151e1295bd7f94df73717351a79f393bc9))

# [1.17.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.16.0...v1.17.0) (2026-06-27)


### Features

* **multisite:** tri du tableau par clic d'en-tête ([#145](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/145), PR 2-2) ([#291](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/291)) ([16b1109](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/16b1109cf3160de923ca3c15e1163e3795dc959f)), closes [#26](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/26)

# [1.16.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.15.0...v1.16.0) (2026-06-27)


### Features

* **carte:** agrégat des carrés du multisite pour la carte ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152), PR 2-1) ([#289](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/289)) ([edd7d8d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/edd7d8d857bfdad4fadbbf350d158f65fa053bbc))

# [1.15.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.14.0...v1.15.0) (2026-06-27)


### Features

* **carte:** composant réutilisable CarteSites (carrés + points) ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152), PR 1) ([#288](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/288)) ([5f21fc3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5f21fc3cd857280c74500ed95d183e1b97a4de2c)), closes [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)

# [1.14.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.13.2...v1.14.0) (2026-06-26)


### Features

* **import:** rapport importés/ignorés/rejetés + dimension doublon ([#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214)) ([#285](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/285)) ([f01da3a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/f01da3ab9b226a491c9d81d16712d3e194ef2613)), closes [#155](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/155) [#147](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/147) [#147](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/147)

## [1.13.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.13.1...v1.13.2) (2026-06-26)


### Bug Fixes

* **test:** débloquer le hang headless introduit par [#283](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/283) (modale native) ([#284](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/284)) ([91a96b6](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/91a96b661b6d9dff8a786dd123ecb15d9fb567f3)), closes [#147](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/147)

## [1.13.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.13.0...v1.13.1) (2026-06-26)


### Bug Fixes

* **import:** rafraîchir la détection de nuit déjà importée au clic ([#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214)) ([#283](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/283)) ([13aeec6](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/13aeec6ce9ec538eeefa45b0086351d48aec640a)), closes [214/#147](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/147) [#280](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/280) [#280](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/280)

# [1.13.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.12.0...v1.13.0) (2026-06-26)


### Features

* **import:** confirmer avant de réimporter une nuit déjà importée ([#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214)) ([#280](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/280)) ([0653834](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0653834149752f8463b5f51e7bae14ece8429308)), closes [214/#147](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/147) [#147](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/147) [#279](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/279)

# [1.12.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.11.0...v1.12.0) (2026-06-26)


### Features

* **import:** écraser un passage en doublon, remplacement atomique ([#279](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/279)) ([#278](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/278)) ([9edf532](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9edf5323aaca0e57528b12ae2ab2fdbf96db1968)), closes [#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214) [#54](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/54) [#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214) [#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214) [#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214) [#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214) [#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214)

# [1.11.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.10.0...v1.11.0) (2026-06-26)


### Features

* **navigation:** nettoyer le temporaire .zip à l'abandon d'un écran d'import ([#230](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/230)) ([#276](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/276)) ([8ace68e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/8ace68ed348637f3793186d680f2a151d641ff6f)), closes [#228](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/228)

# [1.10.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.9.1...v1.10.0) (2026-06-26)


### Features

* **dépôt:** checklist vivante des contrôles de cohérence à l'étape « Préparer » ([#254](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/254)) ([#267](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/267)) ([0c890f8](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0c890f89bda172cc4e2e52cc255b8438a5634686))

## [1.9.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.9.0...v1.9.1) (2026-06-26)


### Bug Fixes

* **navigation:** rafraîchir M-Multisite et M-Site-detail au retour ([#262](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/262)) ([fba1e2b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/fba1e2b954cc30801dc4c7f3f4166d045b767d14)), closes [#260](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/260)

# [1.9.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.8.1...v1.9.0) (2026-06-26)


### Features

* **dépôt:** M-Lot — génération hors-thread + dossier depot/ ouvrable ([#251](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/251)) ([#259](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/259)) ([c4784a8](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/c4784a8de0aff6bbcf5f748d37d279c17af524b9))

## [1.8.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.8.0...v1.8.1) (2026-06-26)


### Bug Fixes

* **navigation:** rafraîchir M-Passage au retour d'une vérification ([#260](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/260)) ([9505be8](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9505be8e32e36c4781dcc76ae41b0d356bcfc988))

# [1.8.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.7.0...v1.8.0) (2026-06-26)


### Features

* **chrome:** barre de défilement centrale quand l'écran dépasse la hauteur ([#256](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/256)) ([074ebb3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/074ebb3358f3cdddd644bd615fb07de7ed1035d5))

# [1.7.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.6.0...v1.7.0) (2026-06-26)


### Features

* **dépôt:** clarifier M-Lot — flux ordonné, stepper et étape « Préparer » explicite ([#251](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/251)) ([d92391f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d92391fc67099f60387bba6bcd35c319e7009edd)), closes [#lblCheminDossier](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/lblCheminDossier)

# [1.6.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.5.0...v1.6.0) (2026-06-26)


### Features

* **captures:** spectrogrammes lisibles dans les aperçus bibliothèque et qualification ([#252](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/252)) ([3794456](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/37944569921ab9a10816fd226b0a5c2fda05c174)), closes [#159](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/159) [#tableEntrees](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/tableEntrees)

# [1.5.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.4.1...v1.5.0) (2026-06-25)


### Features

* **écoute:** activer la normalisation du son à l'écoute ([#109](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/109), [#159](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/159)) ([#248](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/248)) ([e712e8a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e712e8a6c20d0124fec3eca37a14dd979301b4f0))

## [1.4.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.4.0...v1.4.1) (2026-06-25)


### Bug Fixes

* **import:** vérification d'intégrité des fichiers WAV ([#156](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/156)) ([#250](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/250)) ([9907c51](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9907c51d804e0e79d264e89af4f380c923283350)), closes [#155](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/155)

# [1.4.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.3.0...v1.4.0) (2026-06-25)


### Features

* **import:** import résilient + rapport d'anomalies exportable ([#155](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/155)) ([#246](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/246)) ([690d0d9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/690d0d9b2f0fd4904b6c33c3f410b62e938e35ea)), closes [#146](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/146) [#zoneRejets](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/zoneRejets) [#listeRejets](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/listeRejets)

# [1.3.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.2.0...v1.3.0) (2026-06-25)


### Features

* **sites:** activer « Importer une nuit » sur la fiche site (pré-rattachée) ([#245](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/245)) ([7e668cd](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/7e668cdca7b9d41071a8fff8c3a590d663509170))

# [1.2.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.1.0...v1.2.0) (2026-06-25)


### Features

* **style:** re-skin de l'application en indigo (étape 2/2, aligné sur le brief) ([#242](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/242)) ([d6852ed](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d6852ed32bdbe37ea0152d3e0b2ad98cfda68a28)), closes [#4a90d9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/4a90d9) [#3f51b5](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/3f51b5) [#303f9f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/303f9f) [#c5cae9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/c5cae9) [#eef0fa](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/eef0fa) [#1a2a45](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1a2a45) [#1a237e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1a237e) [#b9770e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/b9770e)

# [1.1.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.0.1...v1.1.0) (2026-06-25)


### Features

* **lot:** archives ZIP de dépôt Tadarida (≤ 700 Mo, <préfixe>-N.zip) ([#110](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/110)) ([#239](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/239)) ([4a4e61d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/4a4e61d70fa18090b774cc9e18537a4410519b66)), closes [#104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/104)

## [1.0.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.0.0...v1.0.1) (2026-06-24)


### Bug Fixes

* **installer:** identifiant de bundle macOS valide pour jpackage ([#236](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/236)) ([ca82b79](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ca82b7966a2a487460614546ae8fc9168a835c86))
