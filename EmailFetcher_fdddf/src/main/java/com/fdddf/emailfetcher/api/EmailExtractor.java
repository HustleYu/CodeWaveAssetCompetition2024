package com.fdddf.emailfetcher.api;

import com.fdddf.emailfetcher.*;
import com.netease.lowcode.core.annotation.NaslLogic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Component
public class EmailExtractor {

    /**
     * EmailConfig
     */
    @Resource
    private EmailConfig cfg;
    private static final Logger log = LoggerFactory.getLogger(EmailExtractor.class);
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * Get all folders
     * @return List of folders
     */
    @NaslLogic
    public List<String> getFolders()  {
        String[] includes = new String[]{"INBOX"};
        EmailFetcher fetcher = new EmailFetcher(cfg, includes, null);
        try {
            if (!fetcher.connectToMailBox()) {
                log.error("Can't connect to mailbox");
                return null;
            }

            List <String> folders = fetcher.getFolders();
            fetcher.disconnectFromMailBox();

            return folders;
        } catch (EmailFetchException e) {
            log.error("Can't connect to mailbox");
            return null;
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get all emails
     * @param pageNumber Page number
     * @param pageSize Page size
     * @return List of emails
     */
    @NaslLogic
    public List<Email> getInboxEmails(Integer pageNumber, Integer pageSize) {
        try {
            EmailFetcher fetcher = new EmailFetcher(cfg, null, null);
            if (!fetcher.connectToMailBox()) {
                log.error("Can't connect to mailbox");
                return null;
            }
            List<Email> emails = fetcher.getInboxMails(pageNumber, pageSize);
            fetcher.disconnectFromMailBox();
            return emails;
        } catch (Exception e) {
            log.error("Can't connect to mailbox or fetch emails %s", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Extract emails
     * @param includes List of folders to include
     * @param excludes List of folders to exclude
     * @return List of emails
     */
    @NaslLogic
    public List<Email> extractEmails(List<String> includes, List<String> excludes) {
        String[] incs = includes.toArray(new String[0]);
        String[] excs = excludes.toArray(new String[0]);
        EmailFetcher fetcher = new EmailFetcher(cfg, incs, excs);
        if (!fetcher.connectToMailBox()) {
            log.error("Can't connect to mailbox");
            return null;
        }

        int restartCount = 0;
        String lastFolder = "";

        List<Email> emails = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        while (fetcher.hasNext()) {
            Message mail = fetcher.next();
            if (!lastFolder.equals(fetcher.getFolder())) {
                lastFolder = fetcher.getFolder();
                restartCount = 0;
            }

            try {
                sb.setLength(0);
                fetcher.getPartContent(mail, sb);

                Date receivedDate = mail.getReceivedDate();
                Email email = new Email(mail, fetcher.getFolder());

                for (Address address : mail.getFrom()) {
                    InternetAddress from = (InternetAddress) address;
                    email.from = from.getAddress();
                    System.out.format("from %s %s at %s \n", from.getAddress(), fetcher.getFolder(), FORMAT.format(receivedDate));
                }
                email.content = sb.toString();
                email.receivedDate = FORMAT.format(receivedDate);
                emails.add(email);
            } catch (Exception e) {
                log.error("Can't read content from email", e);

                restartCount++;
                // restart (connect/disconnect) and continue from current folder
                if (restartCount <= 3) {
                    String curFolder = fetcher.getFolder();
                    log.info("Restart at folder {} time {}", curFolder, restartCount);
                    fetcher.disconnectFromMailBox();
                    if (!fetcher.connectToMailBox() || !fetcher.jumpToFolder(curFolder)) {
                        log.info("Jump to folder {} failed. Skip the failed email and continue", curFolder);
                    }
                } else {
                    log.info("Skip the failed email and continue");
                }
            }
        }

        fetcher.disconnectFromMailBox();
        return emails;
    }

    public static void main(String[] args) {
//        EmailConfig config = new EmailConfig();
//        config.protocol = "imap";
//        config.sslEnable = false;
//        config.host = "imap.163.com";
//        config.port = "143";
//        config.username = "aaa@163.com";
//        config.password = "123123";

        List<String> folders = new EmailExtractor().getFolders();
        for (String folder : folders) {
            System.out.printf("----------%s\n", folder);
        }

        System.out.println("--------------------");

        List<Email> emails = new EmailExtractor().getInboxEmails(1, 100);
        for (Email email : emails) {
            System.out.println(email);
        }

        List<String> includes = Arrays.asList("INBOX", "已发送");
        List<String> excludes = Arrays.asList("已删除");
        List<Email> emails2 = new EmailExtractor().extractEmails(includes, excludes);
        for (Email email : emails2) {
            System.out.println(email);
        }
    }

}
