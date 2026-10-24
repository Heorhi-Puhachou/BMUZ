package io.github.heorhipuhachou.bmuz.account;

/** Адпраўка лістоў. Пакуль ёсьць толькі версія, што піша ліст у журнал: сапраўдная (AWS SES) будзе пазьней. */
public interface Mailer {

    void send(String to, String subject, String text);
}
