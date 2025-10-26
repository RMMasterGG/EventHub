# 🚀 EventHub

<div align="center">

[![React](https://img.shields.io/badge/React-61DAFB?style=for-the-badge&logo=react&logoColor=white)](https://reactjs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![WebRTC](https://img.shields.io/badge/WebRTC-333333?style=for-the-badge&logo=webrtc&logoColor=white)](https://webrtc.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![WebSocket](https://img.shields.io/badge/WebSocket-010101?style=for-the-badge&logo=websocket&logoColor=white)](https://websockets.org/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://java.com/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)

**Мощное и безопасное решение для видеоконференций с современным фронтендом на React и надежным бэкендом на Spring Boot.**

</div>

## 📖 Обзор

**VideoConnect** — это полнофункциональная платформа для организации видеовстреч с современным React-фронтендом и высокопроизводительным бэкендом на Java. Система предоставляет богатый REST API и WebSocket-эндпоинты для создания комнат, управления пользователями, потоковой передачи аудио и видео через WebRTC, а также обмена сообщениями в реальном времени.

Идеально подходит для бизнес-встреч, удаленного обучения и неформального общения.

---

## 🛠 Технологический Стек

### Frontend
- **React** - современный UI фреймворк
- **TypeScript** - статическая типизация
- **WebRTC** - видеосвязь в реальном времени
- **WebSocket** - двусторонняя коммуникация

### Backend
- **Spring Boot** - основной фреймворк
- **Java** - язык программирования
- **PostgreSQL** - реляционная база данных
- **WebSocket** - протокол реального времени
- **JWT** - аутентификация и авторизация

---

## ✨ Ключевые Возможности

### 🏠 **Управление Видеокомнатами**
- **Создание комнат "в один клик"** через интуитивно понятный API
- **Генерация уникальных ссылок-приглашений** для безопасного и простого доступа
- **Гибкие настройки комнаты:** заголовок, описание, лимит участников, публичный/приватный доступ
- **Гостевой доступ** — присоединиться к встрече можно без регистрации
- **Валидация и проверка** существования комнат для обеспечения безопасности

### 🔐 **Система Аутентификации и Безопасности**
- **JWT-аутентификация** с access/refresh токенами
- **Полный цикл регистрации и входа**
- **Восстановление и сброс пароля** через email
- **Ролевая модель:** Администратор, Пользователь, Гость

### 👥 **Управление Пользователями и Участниками**
- **Полноценные профили пользователей**
- **Гостевые сессии** с временными профилями
- **Динамический список участников:** Отслеживание всех пользователей в комнате в реальном времени
- **Детальная информация об участниках:** статус, права, состояние аудио/видео

### 🎥 **Высококачественные Медиапотоки (WebRTC)**
- **Передовая технология WebRTC** для передачи аудио и видео с минимальной задержкой
- **Сигнальный сервер** на WebSocket для установления P2P-соединений
- **Стабильность соединения:** Надежная обработка событий подключения и отключения

### 💬 **Текстовый Чат в Реальном Времени**
- **Мгновенная отправка и получение сообщений** через WebSocket
- **Полная история чата:** Все сообщения сохраняются на время сессии
- **Умные уведомления** о новых сообщениях

---

## 🚀 API Reference (Основные эндпоинты)

### **Управление комнатами** (`/api/rooms`)
| Метод | Эндпоинт | Описание | Аутентификация |
|-------|-----------|-----------|----------------|
| `POST` | `/api/rooms` | Создание новой комнаты | ✅ |
| `GET` | `/api/rooms/{roomID}` | Получение информации о комнате | ✅ |
| `DELETE` | `/api/rooms/{roomID}` | Удаление комнаты | ✅ |
| `GET` | `/api/rooms/my-rooms` | Список комнат пользователя | ✅ |
| `POST` | `/api/rooms/{roomID}/join` | Присоединение к комнате | ✅ |
| `POST` | `/api/rooms/{roomID}/leave` | Выход из комнаты | ✅ |
| `GET` | `/api/rooms/{roomID}/participants` | Получить участников комнаты | ✅ |
| `GET` | `/api/rooms/join/{inviteCode}` | Присоединение по invite ссылке | ❌ |

### **Гостевой доступ** (`/api/guest`)
| Метод | Эндпоинт | Описание | Аутентификация |
|-------|-----------|-----------|----------------|
| `POST` | `/api/guest/join` | Присоединиться как гость | ❌ |
| `POST` | `/api/rooms/guest-join` | Гостевой вход в комнату | ❌ |

### **Аутентификация** (`/api/auth`)
| Метод | Эндпоинт | Описание | Аутентификация |
|-------|-----------|-----------|----------------|
| `POST` | `/api/auth/register` | Регистрация пользователя | ❌ |
| `POST` | `/api/auth/login` | Вход в аккаунт | ❌ |
| `POST` | `/api/auth/refresh` | Обновление access token | ✅ |
| `POST` | `/api/auth/logout` | Выход из системы | ✅ |
| `POST` | `/api/auth/recovery-password` | Запрос восстановления пароля | ❌ |
| `POST` | `/api/auth/new-password` | Установка нового пароля | ✅ |

### **Пользователи** (`/api/user`)
| Метод | Эндпоинт | Описание | Аутентификация |
|-------|-----------|-----------|----------------|
| `GET` | `/api/user/me` | Информация о текущем пользователе | ✅ |
| `GET` | `/api/user/profile` | Профиль пользователя | ✅ |

> 📚 **Полная документация API доступна в Swagger UI после запуска приложения по адресу:** `http://localhost:8080/swagger-ui.html`

---

## 🛠 Сборка и Запуск

### Предварительные требования
- **Java 21** или выше
- **Gradle 8.0+**
- **PostgreSQL 14+**
- **Node.js 18+** (для фронтенда)
- **npm** или **yarn**

### Backend
```bash
# Клонирование репозитория
git clone https://github.com/your-username/videoconnect-backend.git
cd videoconnect-backend

# Сборка проекта
mvn clean package

# Запуск приложения
java -jar target/videoconnect-0.1.0.jar
```
### Frontend
```bash
cd frontend

# Установка зависимостей
npm install

# Запуск в режиме разработки
npm start

# Сборка для production
npm run build
```
### Настройка базы данных
Перед запуском убедитесь, что:

1. Сервер PostgreSQL запущен

2. Создана база данных `videoconnect_db`

3. Настройки подключения указаны в `application.yml`:
```bash
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/videoconnect_db
    username: your_username
    password: your_password
```

<div align="center">
Готовы к встрече? Создайте свою первую комнату!
Для начала работы отправьте POST-запрос на /api/rooms

</div>
