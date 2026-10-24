package io.github.heorhipuhachou.bmuz.account;

import io.github.heorhipuhachou.bmuz.account.TeamService.Team;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Адміністратары і ўліковыя запісы выканаўцаў. Толькі для суперадміністратара — гэта правіла ў SecurityConfig. */
@RestController
@RequestMapping("/api/super")
class TeamController {

    record InviteRequest(String email) {
    }

    private final TeamService team;

    TeamController(TeamService team) {
        this.team = team;
    }

    @GetMapping("/admins")
    Team admins() {
        return team.admins();
    }

    @PostMapping("/admins/invites")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void inviteAdmin(@RequestBody InviteRequest body, Authentication auth) {
        team.inviteAdmin(body.email(), auth.getName());
    }

    @DeleteMapping("/admins/invites")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelAdminInvite(@RequestParam String email) {
        team.cancelAdminInvite(email);
    }

    @DeleteMapping("/admins/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeAdmin(@PathVariable long userId) {
        team.removeAdmin(userId);
    }

    @GetMapping("/artists/{artistId}/members")
    Team artistTeam(@PathVariable long artistId) {
        return team.artistTeam(artistId);
    }

    @PostMapping("/artists/{artistId}/invites")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void inviteArtist(@PathVariable long artistId, @RequestBody InviteRequest body, Authentication auth) {
        team.inviteArtist(artistId, body.email(), auth.getName());
    }

    @DeleteMapping("/artists/{artistId}/invites")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelArtistInvite(@PathVariable long artistId, @RequestParam String email) {
        team.cancelArtistInvite(artistId, email);
    }

    @DeleteMapping("/artists/{artistId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(@PathVariable long artistId, @PathVariable long userId) {
        team.removeMember(artistId, userId);
    }
}
