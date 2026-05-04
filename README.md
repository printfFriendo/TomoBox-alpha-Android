🦊 Tomobox Alpha

Um assistente virtual Android inteligente, rápido e com síntese de voz nativa. O Tomobox une o poder de processamento de texto do **Llama 3** (via Groq API) com um motor de Text-to-Speech (TTS) hospedado no Hugging Face, oferecendo uma experiência de chat fluida, interativa e totalmente em português.

✨ Funcionalidades Principais

* 🧠 **Respostas Instantâneas:** Integração com a API do Groq para geração de texto em altíssima velocidade.
* 🗣️ **Voz Integrada (TTS):** As mensagens da IA são lidas em voz alta utilizando um modelo de áudio personalizado, com gerenciamento inteligente de foco de áudio no Android.
* ⚙️ **Processamento em Segundo Plano:** O aplicativo continua gerando a resposta e baixando o áudio mesmo quando minimizado. Uma **notificação silenciosa** avisa quando a assistente está pronta para falar.
* 🎨 **UI/UX Premium:** * Construído 100% em **Jetpack Compose**.
  
    * Suporte dinâmico a **Dark Mode** e **Light Mode**.
    * Bolhas de chat com gradientes modernos.
    * Animação suave de "digitando" (typing indicator).
      
* 🖼️ **Avatar Personalizável:** Permite escolher uma foto de perfil diretamente da galeria do dispositivo, com persistência de dados local (`SharedPreferences`).
* 🥚 **Easter Egg:** Uma pequena surpresa escondida para os usuários curiosos!

## 🛠️ Tecnologias Utilizadas

O projeto foi desenvolvido utilizando as melhores práticas e bibliotecas do ecossistema Android moderno:

* **[Kotlin](https://kotlinlang.org/):** Linguagem principal.
* **[Jetpack Compose](https://developer.android.com/jetpack/compose):** Para a construção da interface de usuário declarativa.
* **[OkHttp3](https://square.github.io/okhttp/):** Para requisições assíncronas e comunicação com as APIs REST (Groq e Hugging Face).
* **[Coil](https://coil-kt.github.io/coil/compose/):** Para o carregamento eficiente e assíncrono das imagens de perfil.
* **Coroutines & Dispatchers:** Para processamento assíncrono e operações de I/O sem travar a thread principal (UI).
* **MediaPlayer & NotificationManager:** Para controle nativo de mídia e serviços de background do Android.

## 🚀 Como Executar o Projeto

Para rodar este projeto na sua máquina, você precisará configurar as suas próprias chaves de API.

1. Faça o clone do repositório:
   ```bash
   git clone [https://github.com/SEU_USUARIO/Tomobox-Android.git](https://github.com/SEU_USUARIO/Tomobox-Android.git)



2. Abra o projeto no Android Studio.

3. Acesse o arquivo MainActivity.kt.

4. Procure pelas funções fetchGroqResponse e fetchVoiceUrl.

5. Substitua o texto "DIGITE_AQUI_SUA_CHAVE_API_... pelas suas credenciais reais do Groq e do Hugging Face.

6. Faça o Build e rode no seu emulador ou dispositivo físico (Android 8.0 Oreo ou superior recomendado).
