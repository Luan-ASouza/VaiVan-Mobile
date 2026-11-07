# VaiVan — Guia de arquitetura

Guia de referência pra manter novas implementações consistentes com o que já existe no projeto. Escrito depois de uma rodada de padronização (nov/2026) que eliminou as inconsistências que existiam antes — o que está descrito aqui é a regra a seguir, não um resumo com exceções.

## Visão geral

App de transporte escolar (van), dois perfis de usuário: **motorista** e **responsável** (pai/responsável pelo aluno). Dois projetos no repositório:
- `app/` — o app Android (Kotlin).
- `admin-web/` — site estático simples (HTML/JS puro, Firebase SDK via CDN, sem build tooling). É também a **raiz de deploy do Firebase**: `admin-web/firebase.json` declara hosting + storage + firestore juntos — por isso `firestore.rules`/`storage.rules` moram lá, não na raiz do repo, e o deploy delas (`firebase deploy`) é manual, **não roda junto com o build do Android**.

Backend: Firebase (Firestore + Storage + Auth).

## Camada de dados (`app/.../data/`)

### Entities (`data/local/entities/`)
- Nomes de campo em português.
- Primeiro campo é sempre `@PrimaryKey val id: String = ""`; último campo é sempre `val lastUpdated: Long = 0L` (exceção documentada: entidades que representam uma linha única sempre sobrescrita, ex. `LocalizacaoViagemEntity`, usam a própria chave de negócio como `@PrimaryKey` e não têm `lastUpdated`).
- Status/enum é sempre `String`, nunca `enum class` — com um comentário ao lado listando os valores possíveis. As constantes desses valores ficam centralizadas no `Repository` correspondente (ex.: `ViagemRepository.STATUS_*`), não na entity.
- Campos denormalizados pra regra do Firestore (`responsavelId`, `motoristaId`, `responsaveisAutorizados`) são um padrão deliberado e recorrente — use-os sempre que uma regra de segurança nova precisar checar dono sem join.
- Entity dobra como modelo de UI por padrão. **Não crie um `Mapper`/modelo paralelo** a menos que haja uma razão real e documentada pra isso (hoje não existe nenhum no projeto).

### DAOs (`data/local/dao/`)
- `upsert`/`upsertAll` via `@Insert(onConflict = OnConflictStrategy.REPLACE)`, sempre `suspend fun`.
- Leituras via `@Query` retornando `Flow<T>`/`Flow<List<T>>`.
- `@Delete`/`deleteById` quando fizer sentido; one-shot `suspend fun` sem `Flow` só quando for pra diffing/sync pontual (não é o padrão geral de leitura).

### Repositories (`data/repository/`)
Firestore = fonte da verdade; Room = cache local que a UI observa. Convenção de verbos (documentada em KDoc nos próprios repositories — siga à risca):
- `observar*` → `Flow` do Room.
- `consultar*` → leitura única direto do Firestore (`suspend fun`), usada quando não se pode confiar no cache (ex.: checar se já existe algo ativo antes de criar).
- `sincronizar*`/`iniciarSincronizacao*` + `pararSincronizacao` → Firestore → Room (sync pontual em batch, ou `addSnapshotListener` contínuo).
- `salvar*` → escreve Firestore, depois Room.
- `excluir*` → apaga Firestore, depois Room.

Construtor sempre recebe `firestore: FirebaseFirestore = FirebaseFirestore.getInstance()` como parâmetro com default. Nome de coleção no Firestore = nome da tabela Room (snake_case português, plural).

Casos legítimos fora desse molde (não é pra forçar o molde neles, mas também não é pra copiar o desvio em repository novo sem necessidade real): um repository que só fala com `FirebaseAuth` (sem Firestore/Room) ou só com `FirebaseStorage` (sem Room) — porque genuinamente não há o que cachear.

### Remote clients (`data/remote/`)
Padrão default: **classe concreta só**, sem interface (ex.: `GoogleRoutesClient`). Só crie uma interface+implementação separada (como `LocalizacaoViagemRemoteSource`/`FirestoreLocalizacaoViagemRemoteSource`) quando houver uma razão real e nomeada pra isolar o backend (ex.: previsão concreta de troca de provedor) — não generalize essa abstração pra todo client novo por "boa prática" genérica.

### `domain/usecase/`
Só pra orquestração de negócio real — múltiplos repositories, uma regra que não pertence a nenhum deles isoladamente (ex.: iniciar viagem, aceitar solicitação, recalcular rota). CRUD simples ou uma leitura de um repository só vai direto ViewModel → Repository, **sem** use case no meio. Nome: `<Verbo><Substantivo>UseCase`. Prefira `suspend operator fun invoke(...)` como ponto de entrada.

## Camada de UI (`app/.../ui/`)

### Pacotes
`ui/<perfil>/<feature>/<Tela>.kt`. Perfis existentes: `motorista`, `responsavel`, `comum`, `admin`, `escolas`, `inicio/cadastro`.

### Activities e `BaseActivity`
Toda Activity de tela cheia que precisa reservar espaço da status bar/nav bar (edge-to-edge) estende `com.example.vaivan.core.base.BaseActivity`, **não** `AppCompatActivity` direto. `BaseActivity` já chama `enableEdgeToEdge()` no `onCreate`; depois do seu próprio `setContentView`, chame `aplicarGapsDeSistema()` (ou com `aplicarTopo`/`aplicarBase = false` se a tela só precisar de um gap) — isso resize os marcadores `topGap`/`bottomGap` do seu layout. Não repita `enableEdgeToEdge()`/`WindowCompat.setDecorFitsSystemWindows`/chamada direta a `SystemBarUtils` numa Activity nova.

`Home<Perfil>Activity` hospeda os Fragments de bottom-nav via um `BottomNavigationController`; fluxos próprios (formulário multi-etapa, mapa, etc.) são Activities dedicadas, não Fragments empilhados num container genérico — telas de mapa em especial **sempre** merecem Activity própria (ciclo de vida de `GoogleMap` previsível).

Sem `BaseFragment` — Fragments estendem `Fragment()` direto.

### Views
`findViewById` em tudo. **Sem** ViewBinding/DataBinding (mesmo com a flag disponível no Gradle). Compose está habilitado no plugin mas **não é usado em lugar nenhum do app** — não introduza uma tela Compose sem alinhar antes, quebraria a consistência do resto do app.

### ViewModel
Construção via a DSL oficial do Jetpack, não uma `object : ViewModelProvider.Factory` escrita à mão:
```kotlin
private val viewModel: XViewModel by viewModels {          // by viewModels (Activity: androidx.activity.viewModels; Fragment: androidx.fragment.app.viewModels)
    viewModelFactory {
        initializer {
            val db = VaivanDatabase.getInstance(this@XActivity)   // "this@NomeDaClasse", nunca "this" bare (dentro do initializer, "this" é o CreationExtras)
            XViewModel(XRepository(db.xDao()))
        }
    }
}
```
Em Fragment, métodos como `requireContext()` resolvem normalmente (sem precisar de qualificação); só o `this` bare precisa de `this@NomeDaClasse`. Se alguma variável construída antes do bloco (`db`, `repository`) também for usada em outro lugar do `onCreate`/`onViewCreated` além do próprio ViewModel, declare-a **fora** do `initializer { }` e capture por closure — não duplique a construção.

ViewModel recebe o(s) repository/use case já prontos no construtor — **nunca** estenda `AndroidViewModel` pra pegar `Application` só pra montar `VaivanDatabase.getInstance(application)` lá dentro; isso força `by viewModels()` sem factory e impede testar/trocar a origem do repository. Sem Hilt/Koin — tudo manual.

### StateFlow e coleta
Exponha estado como `MutableStateFlow` + `.asStateFlow()` (não `Flow` cru). Colete sempre com:
```kotlin
lifecycleScope.launch {           // viewLifecycleOwner.lifecycleScope num Fragment
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.algumFlow.collect { ... }
    }
}
```

### Sessão
`FirebaseAuth.getInstance().currentUser?.uid` direto, onde precisar. Sem wrapper/`SessionManager` — decisão deliberada (projeto sem suíte de testes hoje; o ganho de um wrapper seria só legibilidade).

### Imagens
Glide para qualquer carregamento de imagem remota (foto de perfil, etc.) — `placeholder`/`error` apontando pro drawable genérico correspondente (`ic_perfil`), `.circleCrop()` quando for avatar circular.

### `core/util/`
Funções miscelâneas reaproveitáveis entre features — confira antes de escrever um helper novo: `SystemBarUtils` (gaps de status/nav bar — usado via `BaseActivity`), `WindowInsetsUtil` (telas com teclado/IME visível — problema diferente do gap, não confundir os dois), `PolylineUtil`, `DataUtil` (datas), `GeoUtil`, `MascaraUtil` (máscaras de input), `TextoUtil`.

### Estilo
`colors.xml` em duas camadas: paleta base (hex puro) → classes semânticas (`color_primary`, `status_success`, `status_danger`, `background_screen`, `text_primary`, etc.) — sempre referencie a classe semântica no layout, nunca o hex puro direto. `styles.xml` tem `CircleImage` (avatar circular) e `DiaSemanaButton` reaproveitados em várias telas — reuse antes de criar um style novo equivalente.

## Regras do Firestore (`admin-web/firestore.rules`)

Funções helper já definidas no topo do arquivo: `logado()`, `ehAdmin()`, `assinaturaOperacional(motoristaId)`, `statusMotoristaPermitido()`. Padrão pra uma coleção nova:
- `allow read`: comparar `request.auth.uid` contra o campo denormalizado do dono (`motoristaId`/`responsavelId`/`responsaveisAutorizados`) direto no documento — sem join.
- Quando um documento-filho precisa ler o dono antes do documento-pai existir de fato (criados no mesmo batch), usar `getAfter()`.
- Dado imutável/histórico: `allow update, delete: if false`, com comentário explicando o motivo.
- Lembrar de fazer o deploy manual (`firebase deploy --only firestore:rules`, a partir de `admin-web/`) depois de editar — não é automático.

## Gradle / dependências

`gradle/libs.versions.toml` cobre as libs AndroidX/Jetpack estáveis que já vieram com o projeto. Dependências adicionadas depois (Firebase, Navigation/Places/Maps do Google, Glide) entram como string de versão crua direto no `app/build.gradle.kts` — é a divisão real do projeto, siga-a (não é preciso migrar tudo pro catálogo).

Pegadinha documentada em comentário no `build.gradle.kts`: o Kotlin compiler do projeto é 2.2.10 (lê metadata até 2.3.0) — qualquer lib nova com Kotlin stdlib mais novo que isso quebra a compilação (foi o caso do Places SDK, pinado em 5.3.0 por esse motivo). Verifique a versão do stdlib embutido antes de atualizar uma lib do Google.

Room usa `fallbackToDestructiveMigration()` (sem `Migration` real) — ok em fase de desenvolvimento, mas é dívida técnica: antes de produção precisa de migrações reais, senão todo bump de versão do schema apaga o cache local de todo mundo.
