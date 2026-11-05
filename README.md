# Status Download

Aplicativo Android nativo para visualizar e salvar os arquivos de status disponíveis no aparelho. Interface em português, fundo claro, cabeçalho verde #008476 e ícone minimalista.

## Recursos

- Abas Imagens, Vídeos e Salvos, com duas mídias por linha.
- Navegação entre abas por deslize, mantendo a rolagem independente de cada grade.
- WhatsApp e WhatsApp Business, com autorização de pasta independente.
- Grade limpa, sem etiquetas ou botões sobre as imagens. Os vídeos têm um indicador de reprodução e a duração no canto inferior direito.
- Status já salvos na galeria pelo app recebem apenas uma borda verde fina nas abas Imagens e Vídeos. O destaque acompanha o índice de salvos e é removido quando a cópia deixa de estar disponível.
- 12 idiomas: inglês, português, francês, alemão, italiano, espanhol, árabe, chinês, russo, turco, hindi e indonésio. No primeiro uso, o app acompanha o idioma do dispositivo; há troca manual em **Mais opções → Idioma** e uma opção para voltar ao idioma do dispositivo. Idiomas não disponíveis usam o inglês como alternativa. O árabe usa a interface da direita para a esquerda.
- Descoberta automática das contas do WhatsApp após uma autorização da pasta principal.
- Visualizador interno com reprodução, barra de progresso e navegação entre mídias.
- Pinça e dois toques para zoom em imagens e vídeos; arraste para mover a mídia ampliada.
- Em imagens e vídeos, deslize verticalmente na metade esquerda para brilho. Nos vídeos, a metade direita controla o volume; dois toques na borda esquerda voltam 5 segundos e na direita avançam 5 segundos. No centro, dois toques alternam o zoom. Quando há zoom, arrastar move a mídia.
- Compartilhamento pelo Android, salvamento na galeria sem cópias repetidas e exclusão dos arquivos salvos com confirmação.
- A reprodução pausa ao sair do visualizador. Arquivos e miniaturas são processados fora da interface.

## Primeiro uso

1. Abra e visualize os status no WhatsApp.
2. No Status Download, toque em **Permitir acesso**. O seletor do Android é aberto diretamente em `Android/media/com.whatsapp/WhatsApp` nos aparelhos que respeitam a pasta inicial sugerida.
3. Confirme **Usar esta pasta** e a autorização do sistema. O app procura os status na pasta antiga `Media/.Statuses` e em todas as contas `accounts/*/Media/.Statuses`, incluindo `accounts/1002/Media/.Statuses`. Não é preciso escolher a conta nem exibir pastas ocultas.
4. Para Business, use **Mais opções → Origem: WhatsApp**, escolha WhatsApp Business e autorize a pasta principal correspondente.
5. A lista é atualizada ao voltar ao app. Também é possível usar **Mais opções → Atualizar**.

A autorização persiste ao fechar o app e abrange subpastas e novas contas dentro da pasta escolhida. O Android exige a confirmação inicial; o aplicativo não pode concedê-la sozinho. Alguns seletores de fabricantes podem ignorar a localização sugerida. Nesse caso, escolha a pasta `WhatsApp` em `Android/media/com.whatsapp`, sem navegar até `.Statuses`. Em aparelhos antigos, a pasta pode estar diretamente em `WhatsApp` no armazenamento interno.

Em atualizações com a mesma assinatura, os downloads e a autorização anterior são mantidos. Se ela cobria somente a antiga pasta `Media`, use **Mais opções → Permitir acesso à pasta** uma vez para ampliar o acesso à pasta principal e incluir as contas novas.

O aplicativo lista os arquivos que o WhatsApp disponibiliza localmente. Não acessa status que ainda não foram carregados pelo WhatsApp nem recupera arquivos já removidos. Os nomes dos contatos não estão disponíveis nesses arquivos.

Os downloads ficam em `Pictures/Whats Saver Status` e `Movies/Whats Saver Status`, visíveis na galeria. No Android 10 ou superior, o app usa MediaStore e não pede acesso geral às suas fotos ou a todos os arquivos. Nos Androids 7 a 9, a permissão de escrita é solicitada apenas ao salvar. A origem é lida com a autorização de pasta do Android em todas as versões.

## Compilar

- Android Studio com JDK 17.
- Android SDK 36; Android mínimo 7.0 (API 24).
- Gradle 8.13 e Android Gradle Plugin 8.13.2.

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
```

O APK de teste é gerado em `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions e Play Console

Envie ao repositório o conteúdo do ZIP **do projeto**, com `.github`, `app` e `gradlew` na raiz. A versão inicial preparada para publicação é **1.3 (versionCode 4)**, com nome **Status Download** e identificador `com.toxic.whatssaver`. Aumente o `versionCode` em `app/build.gradle` a cada novo envio à Play Console.

1. Abra **Settings → Secrets and variables → Actions** no repositório.
2. Crie os quatro **Repository secrets** abaixo com os valores do ZIP privado. O nome do secret é o nome do respectivo arquivo sem `.txt`.

   | Secret | Conteúdo |
   | --- | --- |
   | `ANDROID_KEYSTORE_BASE64` | Keystore em Base64, em uma linha |
   | `ANDROID_KEYSTORE_PASSWORD` | Senha do keystore |
   | `ANDROID_KEY_ALIAS` | Alias da chave |
   | `ANDROID_KEY_PASSWORD` | Senha da chave |

3. Em **Actions**, execute **Gerar APK e AAB assinados → Run workflow**, na branch padrão.
4. Baixe em **Artifacts** o `Status-Download-AAB-assinado` para a Play Console e o `Status-Download-APK-assinado` para instalação direta. Cada artefato do GitHub vem em um ZIP; extraia o `.aab` ou `.apk`.
5. Use `design/Status_Download_Icone_512.png` no ícone da ficha da loja.

O workflow **Validar Android** roda compilação de debug e lint sem credenciais. É independente; você não precisa iniciá-lo antes de gerar o release. A publicação é feita manualmente na Play Console.

Use o **Play App Signing** com a chave de distribuição gerenciada pelo Google para um app novo. A chave entregue é a **chave de upload**: assina o AAB enviado à Play Console e o APK de instalação direta produzido pelo workflow. O Google assina os APKs entregues pela loja com sua chave de distribuição. Para testar com a assinatura da loja, instale pela faixa de teste interno. O APK assinado pela nova chave não atualiza por cima do antigo APK de teste da v1.2. Faça backup dos downloads antes de desinstalar uma versão de teste; reinstalar exige conceder novamente o acesso à pasta.

Guarde o ZIP privado fora do repositório. Para compilar localmente, defina `ANDROID_HOME` e os quatro valores em variáveis de ambiente, e execute `bash tools/build-signed.sh`. Também é possível substituir `ANDROID_KEYSTORE_BASE64` por `ANDROID_KEYSTORE_FILE` apontando para o arquivo `.jks`. O script verifica a assinatura dos dois arquivos contra o certificado da chave. O projeto usa Android SDK 36, Build Tools 35.0.0 e JDK 17.

Referências: [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756) · [Secrets do GitHub Actions](https://docs.github.com/actions/security-guides/using-secrets-in-github-actions).

## Testes de integração

Com um emulador Android 10 ou superior:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.toxic.whatssaver.test/com.toxic.whatssaver.SmokeInstrumentation
```

A suíte verifica cópia e leitura na galeria, prevenção de duplicatas, compartilhamento, persistência da sessão, zoom, navegação, brilho, volume, avanço/retrocesso e exclusão preservando a origem. As mídias de teste ficam exclusivamente no APK de testes. A verificação `-e suite storage` também verifica a descoberta de múltiplas contas usando a autorização real concedida no seletor do Android; requer as pastas de teste preparadas no emulador.

Para testar o deslize real entre abas, a duração, a borda dos salvos e os recursos de idioma, prepare as mídias **em um emulador**:

```bash
python3 tools/prepare-emulator-fixtures.py
```

Abra o app e conceda acesso à pasta principal WhatsApp pelo seletor do Android. Com a preferência de idioma em **Idioma do dispositivo**, execute:

```bash
adb shell am instrument -w -e suite interface com.toxic.whatssaver.test/com.toxic.whatssaver.SmokeInstrumentation
adb shell am instrument -w -e suite storage -e images 13 com.toxic.whatssaver.test/com.toxic.whatssaver.SmokeInstrumentation
```

A verificação da interface confirma o idioma inicial, resolve traduções nos 12 idiomas, envia gestos reais para o paginador e confere a posição da duração e a atualização da borda ao salvar/excluir uma cópia. As pastas preparadas incluem a estrutura antiga e as contas 1002 e 1007.

## Dados

O app não contém anúncios, backend ou solicitação de login. Não faz upload de mídias. A autorização da pasta e o índice de downloads ficam no aparelho. O cache de compartilhamento e sessões antigas é limpo automaticamente.

Aplicativo independente, sem vínculo com o WhatsApp ou a Meta. WhatsApp é uma marca de seus respectivos titulares.
