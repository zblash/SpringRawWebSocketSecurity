package com.example.demo.dtos;

import com.example.demo.models.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WrapperWsNotification implements Serializable {

    private User user;

    private String notification;

}
